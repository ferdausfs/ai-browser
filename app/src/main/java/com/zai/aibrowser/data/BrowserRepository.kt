package com.zai.aibrowser.data

import androidx.room.withTransaction
import com.zai.aibrowser.data.db.AppDatabase
import com.zai.aibrowser.data.db.BookmarkEntity
import com.zai.aibrowser.data.db.HistoryEntity
import com.zai.aibrowser.data.db.TabEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single door to all browser data (Room-backed).
 * The UI and browser layers never touch DAOs directly, so swapping the storage
 * backend in a later phase touches exactly one file.
 */
class BrowserRepository(private val db: AppDatabase) {

    // ---- Tabs ----

    fun observeTabs(): Flow<List<TabEntity>> = db.tabDao().observeAll()

    suspend fun getTabs(): List<TabEntity> = db.tabDao().getAll()

    suspend fun addTab(url: String, title: String, position: Int, isActive: Boolean): Long {
        return db.withTransaction {
            if (isActive) db.tabDao().deactivateAll()
            db.tabDao().insert(
                TabEntity(
                    url = url,
                    title = title,
                    position = position,
                    isActive = isActive,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateNavigation(tabId: Long, url: String, title: String) =
        db.tabDao().updateNavigation(tabId, url, title, System.currentTimeMillis())

    suspend fun deleteTab(tabId: Long) {
        db.withTransaction { db.tabDao().deleteById(tabId) }
    }

    suspend fun clearTabs() {
        db.withTransaction { db.tabDao().clear() }
    }

    suspend fun setActiveTab(tabId: Long) {
        db.withTransaction {
            db.tabDao().deactivateAll()
            db.tabDao().activate(tabId)
        }
    }

    // ---- Bookmarks ----

    fun observeBookmarks(): Flow<List<BookmarkEntity>> = db.bookmarkDao().observeAll()

    fun observeIsBookmarked(url: String): Flow<Boolean> = db.bookmarkDao().observeIsBookmarked(url)

    suspend fun isBookmarked(url: String): Boolean = db.bookmarkDao().isBookmarked(url)

    suspend fun addBookmark(url: String, title: String) =
        db.bookmarkDao().insert(
            BookmarkEntity(url = url, title = title, createdAt = System.currentTimeMillis())
        )

    suspend fun removeBookmark(url: String) = db.bookmarkDao().deleteByUrl(url)

    suspend fun clearBookmarks() = db.bookmarkDao().clear()

    // ---- History ----

    fun observeHistory(limit: Int): Flow<List<HistoryEntity>> = db.historyDao().observeRecent(limit)

    suspend fun addHistory(url: String, title: String) =
        db.historyDao().insert(
            HistoryEntity(url = url, title = title, visitedAt = System.currentTimeMillis())
        )

    suspend fun clearHistory() = db.historyDao().clear()

    suspend fun deleteHistoryBefore(cutoff: Long) = db.historyDao().deleteOlderThan(cutoff)
}
