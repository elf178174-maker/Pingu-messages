package app.pingu.messages.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.util.Log
import app.pingu.messages.core.text.SearchMatcher
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.entity.BlockedNumberEntity
import app.pingu.messages.data.local.entity.SpamKeywordEntity
import app.pingu.messages.data.telephony.CursorUtils.queryAll
import app.pingu.messages.data.telephony.CursorUtils.stringOrNull
import app.pingu.messages.domain.model.BlockOrigin
import app.pingu.messages.domain.model.BlockedNumber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Blocking and spam.
 *
 * Android has a system-wide blocked-number list that only the default SMS app and the default
 * dialer may write to. Pingu Messages holds the SMS role, so blocking here also blocks calls and
 * survives switching to another messaging app - which is what a person means by "block this
 * number". The local table is kept in step and is the fallback on devices or profiles where the
 * platform refuses access (work profiles and secondary users cannot use it at all).
 *
 * Spam keyword filtering is separate and entirely local: an incoming message from a number that is
 * not in the contact list is checked against the user's own keyword list. No message is uploaded
 * anywhere and there is no server-side classifier pretending to be one.
 */
class BlockedNumberRepository(
    private val context: Context,
    private val database: PinguDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val dao get() = database.blockedNumberDao()

    fun observeBlocked(): Flow<List<BlockedNumber>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeBlockedKeys(): Flow<Set<String>> = dao.observeMatchKeys().map { it.toSet() }

    fun observeSpamKeywords(): Flow<List<SpamKeywordEntity>> = dao.observeKeywords()

    /** True when the platform lets this app read and write the system block list. */
    fun canUseSystemBlockList(): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    } catch (error: Exception) {
        false
    }

    suspend fun isBlocked(address: String): Boolean = withContext(ioDispatcher) {
        val key = PhoneNumbers.matchKey(address)
        key.isNotEmpty() && dao.isBlocked(key)
    }

    suspend fun blockedKeys(): Set<String> = withContext(ioDispatcher) {
        dao.allMatchKeys().toSet()
    }

    suspend fun block(
        address: String,
        origin: BlockOrigin = BlockOrigin.MANUAL,
        note: String? = null,
    ) = withContext(ioDispatcher) {
        val key = PhoneNumbers.matchKey(address)
        if (key.isEmpty()) return@withContext
        val syncedToSystem = addToSystemList(address)
        dao.insert(
            BlockedNumberEntity(
                address = address,
                matchKey = key,
                origin = origin.name,
                blockedAt = System.currentTimeMillis(),
                syncedToSystem = syncedToSystem,
                note = note,
            ),
        )
    }

    suspend fun unblock(address: String) = withContext(ioDispatcher) {
        val key = PhoneNumbers.matchKey(address)
        if (key.isEmpty()) return@withContext
        removeFromSystemList(address)
        dao.deleteByMatchKey(key)
    }

    /**
     * Pulls the platform's block list into the local one, so numbers blocked from the dialer or
     * from another app are honoured here too.
     */
    suspend fun importSystemBlockList() = withContext(ioDispatcher) {
        if (!canUseSystemBlockList()) return@withContext
        val existing = dao.allMatchKeys().toSet()
        val systemNumbers = context.contentResolver.queryAll(
            uri = BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            projection = arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            mapper = { it.stringOrNull(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER) },
        )
        systemNumbers.forEach { number ->
            val key = PhoneNumbers.matchKey(number)
            if (key.isNotEmpty() && key !in existing) {
                dao.insert(
                    BlockedNumberEntity(
                        address = number,
                        matchKey = key,
                        origin = BlockOrigin.SYSTEM.name,
                        blockedAt = System.currentTimeMillis(),
                        syncedToSystem = true,
                    ),
                )
            }
        }
    }

    // ---- Spam keywords ------------------------------------------------------------------------

    suspend fun addSpamKeyword(keyword: String) = withContext(ioDispatcher) {
        val cleaned = keyword.trim()
        if (cleaned.isNotEmpty()) {
            dao.insertKeyword(SpamKeywordEntity(keyword = cleaned, createdAt = System.currentTimeMillis()))
        }
    }

    suspend fun removeSpamKeyword(id: Long) = withContext(ioDispatcher) { dao.deleteKeyword(id) }

    /**
     * True when a message from an unknown sender matches one of the user's spam keywords.
     * Messages from people in the contact list are never filtered.
     */
    suspend fun looksLikeSpam(body: String?, senderIsKnownContact: Boolean): Boolean =
        withContext(ioDispatcher) {
            if (senderIsKnownContact || body.isNullOrBlank()) return@withContext false
            val keywords = dao.keywords()
            keywords.any { SearchMatcher.contains(body, it) }
        }

    private fun addToSystemList(address: String): Boolean {
        if (!canUseSystemBlockList()) return false
        return try {
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, address)
                },
            ) != null
        } catch (error: Exception) {
            Log.d(TAG, "System block list refused $address", error)
            false
        }
    }

    private fun removeFromSystemList(address: String) {
        if (!canUseSystemBlockList()) return
        try {
            BlockedNumberContract.unblock(context, address)
        } catch (error: Exception) {
            Log.d(TAG, "System block list refused to unblock $address", error)
        }
    }

    private companion object {
        const val TAG = "BlockedNumbers"
    }
}

private fun BlockedNumberEntity.toDomain(): BlockedNumber = BlockedNumber(
    id = id,
    address = address,
    matchKey = matchKey,
    origin = runCatching { BlockOrigin.valueOf(origin) }.getOrElse { BlockOrigin.MANUAL },
    blockedAt = blockedAt,
    syncedToSystem = syncedToSystem,
    note = note,
)
