package app.pingu.messages.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A blocked sender.
 *
 * [matchKey] is the normalised comparison key, so `+44 7700 900123` and `07700900123` cannot both
 * end up on the list as separate entries.
 */
@Entity(
    tableName = "blocked_numbers",
    indices = [Index(value = ["matchKey"], unique = true)],
)
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val address: String,
    val matchKey: String,
    val origin: String,
    val blockedAt: Long,
    @ColumnInfo(defaultValue = "0")
    val syncedToSystem: Boolean = false,
    val note: String? = null,
)

/**
 * A keyword that marks an incoming message from an unknown sender as spam.
 *
 * Filtering runs entirely on device against the message that just arrived; nothing is uploaded and
 * there is no scoring model pretending to be smarter than it is.
 */
@Entity(
    tableName = "spam_keywords",
    indices = [Index(value = ["keyword"], unique = true)],
)
data class SpamKeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val keyword: String,
    val createdAt: Long,
)
