package app.pingu.messages.data.repository

import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.dao.FolderWithCount
import app.pingu.messages.data.local.entity.FolderEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Custom folders, an organisation layer Google Messages does not offer.
 *
 * A folder is purely local: it never changes how a message is sent or stored, so removing a folder
 * leaves its conversations untouched in the main list.
 */
class FolderRepository(
    private val database: PinguDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val dao get() = database.folderDao()

    fun observeFolders(): Flow<List<FolderWithCount>> = dao.observeAll()

    suspend fun create(name: String, colorSlot: Int): Long = withContext(ioDispatcher) {
        dao.insert(
            FolderEntity(
                name = name.trim(),
                colorSlot = colorSlot,
                position = Int.MAX_VALUE,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun rename(folder: FolderWithCount, name: String) = withContext(ioDispatcher) {
        dao.update(
            FolderEntity(
                id = folder.id,
                name = name.trim(),
                colorSlot = folder.colorSlot,
                position = folder.position,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: Long) = withContext(ioDispatcher) {
        dao.detachConversations(id)
        dao.delete(id)
    }
}
