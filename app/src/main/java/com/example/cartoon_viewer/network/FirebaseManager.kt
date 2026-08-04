package com.example.cartoon_viewer.network

import android.util.Log
import com.example.cartoon_viewer.model.BookmarkedChapter
import com.example.cartoon_viewer.model.Chapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseManager {
    private val TAG = "FirebaseManager"
    
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (t: Throwable) {
            Log.e(TAG, "FirebaseAuth initialization failed", t)
            null
        }
    }
    
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (t: Throwable) {
            Log.e(TAG, "FirebaseFirestore initialization failed", t)
            null
        }
    }

    val currentUser get() = auth?.currentUser

    suspend fun ensureAuthenticated(): String? {
        val a = auth ?: return null
        if (a.currentUser == null) {
            return null
        }
        return a.currentUser?.uid
    }

    suspend fun signInWithGoogle(idToken: String): String? {
        val a = auth ?: return null
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val result = a.signInWithCredential(credential).await()
            result.user?.uid
        } catch (e: Exception) {
            Log.e(TAG, "Google sign in failed", e)
            null
        }
    }

    fun signOut() {
        auth?.signOut()
    }

    suspend fun saveLastRead(chapter: BookmarkedChapter) {
        val uid = ensureAuthenticated() ?: return
        val database = db ?: return
        val data = mapOf(
            "mangaId" to chapter.mangaId,
            "mangaTitle" to chapter.mangaTitle,
            "mangaUrl" to chapter.mangaUrl,
            "chapterId" to chapter.chapter.id,
            "chapterTitle" to chapter.chapter.title,
            "chapterLink" to chapter.chapter.link,
            "chapterThumb" to chapter.chapter.thumbnailUrl,
            "chapterDate" to chapter.chapter.date,
            "pageIndex" to chapter.pageIndex,
            "timestamp" to System.currentTimeMillis()
        )
        try {
            Log.d(TAG, "Saving last read to cloud for user: $uid")
            database.collection("users").document(uid)
                .collection("sync").document("lastRead")
                .set(data)
                .await()
            Log.d(TAG, "Successfully saved last read to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save last read to cloud", e)
        }
    }

    suspend fun loadLastRead(): BookmarkedChapter? {
        val uid = ensureAuthenticated() ?: return null
        val database = db ?: return null
        try {
            val doc = database.collection("users").document(uid)
                .collection("sync").document("lastRead")
                .get()
                .await()
            
            if (!doc.exists()) return null
            
            return BookmarkedChapter(
                mangaId = doc.getString("mangaId") ?: "",
                mangaTitle = doc.getString("mangaTitle") ?: "",
                mangaUrl = doc.getString("mangaUrl") ?: "",
                chapter = Chapter(
                    id = doc.getString("chapterId") ?: "",
                    title = doc.getString("chapterTitle") ?: "",
                    link = doc.getString("chapterLink") ?: "",
                    thumbnailUrl = doc.getString("chapterThumb") ?: "",
                    date = doc.getString("chapterDate") ?: ""
                ),
                pageIndex = doc.getLong("pageIndex")?.toInt() ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load last read", e)
            return null
        }
    }

    suspend fun syncBookmarks(localBookmarks: List<BookmarkedChapter>): List<BookmarkedChapter> {
        val uid = ensureAuthenticated() ?: return localBookmarks
        val database = db ?: return localBookmarks
        
        try {
            // 1. Get cloud bookmarks
            val cloudDocs = database.collection("users").document(uid)
                .collection("bookmarks")
                .get()
                .await()
            
            val cloudBookmarks = cloudDocs.map { doc ->
                BookmarkedChapter(
                    mangaId = doc.getString("mangaId") ?: "",
                    mangaTitle = doc.getString("mangaTitle") ?: "",
                    mangaUrl = doc.getString("mangaUrl") ?: "",
                    chapter = Chapter(
                        id = doc.id,
                        title = doc.getString("chapterTitle") ?: "",
                        link = doc.getString("chapterLink") ?: "",
                        thumbnailUrl = doc.getString("chapterThumb") ?: "",
                        date = doc.getString("chapterDate") ?: ""
                    ),
                    pageIndex = doc.getLong("pageIndex")?.toInt() ?: 0
                )
            }

            // 2. Merge logic
            val merged = (localBookmarks + cloudBookmarks).distinctBy { it.chapter.id }
            
            // 3. Upload missing ones
            merged.forEach { b ->
                if (cloudBookmarks.none { it.chapter.id == b.chapter.id }) {
                    saveBookmarkToCloud(uid, b)
                }
            }
            
            return merged
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync bookmarks", e)
            return localBookmarks
        }
    }

    suspend fun saveBookmarkToCloud(uid: String, b: BookmarkedChapter) {
        val database = db ?: return
        val data = mapOf(
            "mangaId" to b.mangaId,
            "mangaTitle" to b.mangaTitle,
            "mangaUrl" to b.mangaUrl,
            "chapterTitle" to b.chapter.title,
            "chapterLink" to b.chapter.link,
            "chapterThumb" to b.chapter.thumbnailUrl,
            "chapterDate" to b.chapter.date,
            "pageIndex" to b.pageIndex
        )
        try {
            Log.d(TAG, "Saving bookmark to cloud: ${b.chapter.title}")
            database.collection("users").document(uid)
                .collection("bookmarks").document(b.chapter.id)
                .set(data)
                .await()
            Log.d(TAG, "Successfully saved bookmark to cloud")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bookmark to cloud", e)
        }
    }

    suspend fun deleteBookmarkFromCloud(chapterId: String) {
        val uid = ensureAuthenticated() ?: return
        val database = db ?: return
        try {
            database.collection("users").document(uid)
                .collection("bookmarks").document(chapterId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete bookmark", e)
        }
    }
}
