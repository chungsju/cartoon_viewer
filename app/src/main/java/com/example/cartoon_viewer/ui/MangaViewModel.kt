package com.example.cartoon_viewer.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cartoon_viewer.model.Chapter
import com.example.cartoon_viewer.model.DownloadedChapter
import com.example.cartoon_viewer.model.BookmarkedChapter
import com.example.cartoon_viewer.model.Manga
import com.example.cartoon_viewer.model.MangaDetail
import com.example.cartoon_viewer.model.MangaPage
import com.example.cartoon_viewer.ui.screens.ViewMode
import com.example.cartoon_viewer.ui.screens.ReadingDirection
import com.example.cartoon_viewer.network.LocalMangaManager
import com.example.cartoon_viewer.network.FirebaseManager
import com.example.cartoon_viewer.network.SpotvScraper
import com.example.cartoon_viewer.network.UrlProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MangaViewModel(application: Application) : AndroidViewModel(application) {
    private val urlProvider = UrlProvider(application)
    private val scraper = SpotvScraper(application)
    private val localManager = LocalMangaManager(application)
    private val firebaseManager = FirebaseManager()

    // SharedPreferences for local storage
    private val bookmarkPrefs = application.getSharedPreferences("bookmarks", android.content.Context.MODE_PRIVATE)
    private val lastReadPrefs = application.getSharedPreferences("last_read", android.content.Context.MODE_PRIVATE)

    private val _mangaList = MutableStateFlow<List<Manga>>(emptyList())
    val mangaList: StateFlow<List<Manga>> = _mangaList

    private val _mangaDetail = MutableStateFlow<MangaDetail?>(null)
    val mangaDetail: StateFlow<MangaDetail?> = _mangaDetail

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _pages = MutableStateFlow<List<MangaPage>>(emptyList())
    val pages: StateFlow<List<MangaPage>> = _pages

    private val _downloadedChapters = MutableStateFlow<List<DownloadedChapter>>(emptyList())
    val downloadedChapters: StateFlow<List<DownloadedChapter>> = _downloadedChapters

    private val _bookmarkedChapters = MutableStateFlow<List<BookmarkedChapter>>(emptyList())
    val bookmarkedChapters: StateFlow<List<BookmarkedChapter>> = _bookmarkedChapters

    private val _lastRead = MutableStateFlow<BookmarkedChapter?>(null)
    val lastRead: StateFlow<BookmarkedChapter?> = _lastRead

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isDownloading = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isDownloading: StateFlow<Map<String, Boolean>> = _isDownloading

    private var currentPage = 1
    private var currentSca = "all"
    private var currentType = "over"
    private var currentBaseUrl = ""
    private var currentChapterPage = 1
    private var currentMangaUrl = ""
    private var currentMangaId = ""
    private val _isEndReached = MutableStateFlow(false)
    val isEndReached: StateFlow<Boolean> = _isEndReached
    private val _isChaptersEndReached = MutableStateFlow(false)
    val isChaptersEndReached: StateFlow<Boolean> = _isChaptersEndReached

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail

    // Viewer Settings (Global)
    private val _viewMode = MutableStateFlow(ViewMode.SINGLE)
    val viewMode: StateFlow<ViewMode> = _viewMode

    private val _readingDirection = MutableStateFlow(ReadingDirection.LTR)
    val readingDirection: StateFlow<ReadingDirection> = _readingDirection

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen

    init {
        try {
            _userEmail.value = firebaseManager.currentUser?.email
            if (_userEmail.value != null) {
                syncCloudData()
            } else {
                loadLastRead()
                loadBookmarkedChapters()
            }
        } catch (e: Exception) {
            Log.e("MangaViewModel", "Firebase init failed", e)
            loadLastRead()
            loadBookmarkedChapters()
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            val uid = firebaseManager.signInWithGoogle(idToken)
            if (uid != null) {
                _userEmail.value = firebaseManager.currentUser?.email
                syncCloudData()
            }
        }
    }

    fun signOut() {
        firebaseManager.signOut()
        _userEmail.value = null
        loadLastRead()
        loadBookmarkedChapters()
    }

    fun syncCloudData() {
        viewModelScope.launch {
            try {
                firebaseManager.ensureAuthenticated()
                // 1. Sync Last Read from cloud
                val cloudLastRead = firebaseManager.loadLastRead()
                if (cloudLastRead != null) {
                    saveLastRead(cloudLastRead.mangaId, cloudLastRead.mangaTitle, cloudLastRead.mangaUrl, cloudLastRead.chapter, cloudLastRead.pageIndex, syncToCloud = false)
                } else {
                    loadLastRead()
                }

                // 2. Sync Bookmarks
                loadBookmarkedChapters() // Load local first
                val syncedBookmarks = firebaseManager.syncBookmarks(_bookmarkedChapters.value)
                
                // Save synced to local
                val bookmarks = syncedBookmarks.map { b ->
                    "${b.mangaId}|${b.mangaTitle}|${b.mangaUrl}|${b.chapter.id}|${b.chapter.title}|${b.chapter.link}|${b.chapter.thumbnailUrl}|${b.chapter.date}|${b.pageIndex}"
                }.toSet()
                bookmarkPrefs.edit().putStringSet("bookmark_list", bookmarks).apply()
                _bookmarkedChapters.value = syncedBookmarks
            } catch (e: Exception) {
                Log.e("MangaViewModel", "Cloud sync failed", e)
                loadLastRead()
                loadBookmarkedChapters()
            }
        }
    }

    fun updateViewMode(mode: ViewMode) { _viewMode.value = mode }
    fun updateReadingDirection(dir: ReadingDirection) { _readingDirection.value = dir }
    fun updateFullscreen(full: Boolean) { _isFullscreen.value = full }

    fun getBaseUrl(): String = urlProvider.baseUrl
    fun updateBaseUrl(url: String) {
        urlProvider.baseUrl = url
    }
    fun getCategoryUrl(categoryTitle: String) = urlProvider.getCategoryUrl(categoryTitle)

    fun loadMangaList(url: String, sca: String? = null) {
        currentPage = 1
        _isEndReached.value = false
        currentBaseUrl = url.substringBefore("&page=")
        currentSca = if (sca == null || sca == "전체") "all" else sca
        currentType = when {
            url.contains("is_over=1") -> "over"
            url.contains("type=upd") -> "upd"
            url.contains("type=today") -> "today"
            else -> "rank"
        }

        val finalUrl = if (sca != null && sca != "전체") {
            "$url&sca=${java.net.URLEncoder.encode(sca, "UTF-8")}"
        } else url
        
        viewModelScope.launch {
            _isLoading.value = true
            _mangaList.value = scraper.fetchMangaList(finalUrl)
            _isLoading.value = false
        }
    }

    fun loadMoreManga() {
        if (_isLoading.value || _isEndReached.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            val nextPage = currentPage + 1
            
            val nextList = if (currentType == "upd") {
                val pagedUrl = if (currentBaseUrl.contains("?")) "$currentBaseUrl&page=$nextPage" else "$currentBaseUrl?page=$nextPage"
                val finalUrl = if (currentSca != "all") "$pagedUrl&sca=${java.net.URLEncoder.encode(currentSca, "UTF-8")}" else pagedUrl
                scraper.fetchMangaList(finalUrl)
            } else {
                scraper.fetchMoreManga(nextPage, currentSca, currentType)
            }

            val isDuplicate = nextList.any { next -> _mangaList.value.any { existing -> existing.id == next.id } }

            if (nextList.isEmpty() || isDuplicate) {
                _isEndReached.value = true
            } else {
                _mangaList.value = _mangaList.value + nextList
                currentPage = nextPage
            }
            _isLoading.value = false
        }
    }

    fun searchManga(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _mangaList.value = scraper.searchManga(query)
            _isLoading.value = false
        }
    }

    fun loadChapters(mangaUrl: String, mangaId: String) {
        currentChapterPage = 1
        currentMangaUrl = mangaUrl
        currentMangaId = mangaId
        _isChaptersEndReached.value = false
        
        viewModelScope.launch {
            _isLoading.value = true
            val detail = scraper.fetchMangaDetail(mangaUrl)
            _mangaDetail.value = detail
            _chapters.value = detail.chapters.map { 
                it.copy(isDownloaded = localManager.isChapterDownloaded(mangaId, it.id))
            }
            _isLoading.value = false
        }
    }

    fun loadMoreChapters() {
        if (_isLoading.value || _isChaptersEndReached.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            val nextPage = currentChapterPage + 1
            val pagedUrl = if (currentMangaUrl.contains("?")) "$currentMangaUrl&page=$nextPage" else "$currentMangaUrl?page=$nextPage"
            
            val nextList = scraper.fetchChapters(pagedUrl)
            val isDuplicate = nextList.any { next -> _chapters.value.any { existing -> existing.id == next.id } }
            
            if (nextList.isEmpty() || isDuplicate) {
                _isChaptersEndReached.value = true
            } else {
                val processedList = nextList.map { 
                    it.copy(isDownloaded = localManager.isChapterDownloaded(currentMangaId, it.id))
                }
                _chapters.value = _chapters.value + processedList
                currentChapterPage = nextPage
            }
            _isLoading.value = false
        }
    }

    fun loadPages(chapterUrl: String, mangaId: String, chapterId: String) {
        if (chapterUrl == "local_zip") return
        
        viewModelScope.launch {
            _isLoading.value = true
            if (localManager.isChapterDownloaded(mangaId, chapterId)) {
                _pages.value = localManager.getLocalPages(mangaId, chapterId)
            } else {
                _pages.value = scraper.fetchPages(chapterUrl)
            }
            _isLoading.value = false
        }
    }

    fun loadZipFile(inputStream: java.io.InputStream, title: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _pages.value = localManager.extractZip(inputStream, title)
            _isLoading.value = false
        }
    }

    fun downloadChapter(mangaId: String, mangaTitle: String, chapter: Chapter) {
        viewModelScope.launch {
            _isDownloading.value = _isDownloading.value + (chapter.id to true)
            val pages = scraper.fetchPages(chapter.link)
            val success = localManager.downloadChapter(mangaId, mangaTitle, chapter, pages)
            _isDownloading.value = _isDownloading.value - chapter.id
            
            if (success) {
                _chapters.value = _chapters.value.map {
                    if (it.id == chapter.id) it.copy(isDownloaded = true) else it
                }
                loadDownloadedChapters()
            }
        }
    }

    fun loadDownloadedChapters() {
        _downloadedChapters.value = localManager.getAllDownloadedChapters()
    }

    fun loadBookmarkedChapters() {
        val bookmarks = bookmarkPrefs.getStringSet("bookmark_list", emptySet()) ?: emptySet()
        _bookmarkedChapters.value = bookmarks.mapNotNull { data ->
            val parts = data.split("|")
            if (parts.size >= 8) {
                BookmarkedChapter(
                    mangaId = parts[0],
                    mangaTitle = parts[1],
                    mangaUrl = parts[2],
                    chapter = Chapter(
                        id = parts[3],
                        title = parts[4],
                        link = parts[5],
                        thumbnailUrl = parts[6],
                        date = parts[7]
                    ),
                    pageIndex = if (parts.size >= 9) parts[8].toIntOrNull() ?: 0 else 0
                )
            } else null
        }.sortedByDescending { it.chapter.date }
    }

    fun toggleBookmark(mangaId: String, mangaTitle: String, mangaUrl: String, chapter: Chapter, pageIndex: Int) {
        val bookmarks = bookmarkPrefs.getStringSet("bookmark_list", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val existingEntry = bookmarks.find { 
            it.startsWith("$mangaId|") && it.contains("|${chapter.id}|") 
        }

        if (existingEntry != null) {
            bookmarks.remove(existingEntry)
            viewModelScope.launch {
                firebaseManager.deleteBookmarkFromCloud(chapter.id)
            }
        } else {
            val thumb = chapter.thumbnailUrl.ifEmpty {
                _chapters.value.find { it.id == chapter.id }?.thumbnailUrl ?: ""
            }
            val entryString = "$mangaId|$mangaTitle|$mangaUrl|${chapter.id}|${chapter.title}|${chapter.link}|$thumb|${chapter.date}|$pageIndex"
            bookmarks.add(entryString)
            viewModelScope.launch {
                val b = BookmarkedChapter(mangaId, mangaTitle, mangaUrl, chapter.copy(thumbnailUrl = thumb), pageIndex)
                firebaseManager.ensureAuthenticated()?.let { uid ->
                    firebaseManager.saveBookmarkToCloud(uid, b)
                }
            }
        }
        
        bookmarkPrefs.edit().putStringSet("bookmark_list", bookmarks).apply()
        loadBookmarkedChapters()
    }

    fun isBookmarked(mangaId: String, chapterId: String): Boolean {
        val bookmarks = bookmarkPrefs.getStringSet("bookmark_list", emptySet()) ?: emptySet()
        return bookmarks.any { it.startsWith("$mangaId|") && it.contains("|$chapterId|") }
    }

    fun deleteBookmark(chapterId: String) {
        val bookmarks = bookmarkPrefs.getStringSet("bookmark_list", emptySet())?.toMutableSet() ?: mutableSetOf()
        val entry = bookmarks.find { it.contains("|$chapterId|") }
        if (entry != null) {
            bookmarks.remove(entry)
            bookmarkPrefs.edit().putStringSet("bookmark_list", bookmarks).apply()
            loadBookmarkedChapters()
            viewModelScope.launch {
                firebaseManager.deleteBookmarkFromCloud(chapterId)
            }
        }
    }

    fun saveLastRead(mangaId: String, mangaTitle: String, mangaUrl: String, chapter: Chapter, pageIndex: Int, syncToCloud: Boolean = true) {
        val data = "$mangaId|$mangaTitle|$mangaUrl|${chapter.id}|${chapter.title}|${chapter.link}|${chapter.thumbnailUrl}|${chapter.date}|$pageIndex"
        lastReadPrefs.edit().putString("last_data", data).apply()
        loadLastRead()
        
        if (syncToCloud) {
            viewModelScope.launch {
                firebaseManager.saveLastRead(BookmarkedChapter(mangaId, mangaTitle, mangaUrl, chapter, pageIndex))
            }
        }
    }

    fun loadLastRead() {
        val data = lastReadPrefs.getString("last_data", null)
        if (data != null) {
            val parts = data.split("|")
            if (parts.size >= 8) {
                _lastRead.value = BookmarkedChapter(
                    mangaId = parts[0],
                    mangaTitle = parts[1],
                    mangaUrl = parts[2],
                    chapter = Chapter(
                        id = parts[3],
                        title = parts[4],
                        link = parts[5],
                        thumbnailUrl = parts[6],
                        date = parts[7]
                    ),
                    pageIndex = if (parts.size >= 9) parts[8].toIntOrNull() ?: 0 else 0
                )
            }
        }
    }

    fun deleteChapter(mangaId: String, chapterId: String) {
        localManager.deleteChapter(mangaId, chapterId)
        _chapters.value = _chapters.value.map {
            if (it.id == chapterId) it.copy(isDownloaded = false) else it
        }
        loadDownloadedChapters()
    }
}
