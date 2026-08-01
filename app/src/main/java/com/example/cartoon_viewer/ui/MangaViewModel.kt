package com.example.cartoon_viewer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cartoon_viewer.model.Chapter
import com.example.cartoon_viewer.model.DownloadedChapter
import com.example.cartoon_viewer.model.Manga
import com.example.cartoon_viewer.model.MangaPage
import com.example.cartoon_viewer.network.LocalMangaManager
import com.example.cartoon_viewer.network.SpotvScraper
import com.example.cartoon_viewer.network.UrlProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MangaViewModel(application: Application) : AndroidViewModel(application) {
    private val urlProvider = UrlProvider(application)
    private val scraper = SpotvScraper(application)
    private val localManager = LocalMangaManager(application)

    private val _mangaList = MutableStateFlow<List<Manga>>(emptyList())
    val mangaList: StateFlow<List<Manga>> = _mangaList

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

    private val _pages = MutableStateFlow<List<MangaPage>>(emptyList())
    val pages: StateFlow<List<MangaPage>> = _pages

    private val _downloadedChapters = MutableStateFlow<List<DownloadedChapter>>(emptyList())
    val downloadedChapters: StateFlow<List<DownloadedChapter>> = _downloadedChapters

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

            // Check if we already have these items to avoid infinite duplicates
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
            val list = scraper.fetchChapters(mangaUrl)
            _chapters.value = list.map { 
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
            
            // Check if we already have these chapters to avoid infinite duplicates
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

    fun deleteChapter(mangaId: String, chapterId: String) {
        localManager.deleteChapter(mangaId, chapterId)
        _chapters.value = _chapters.value.map {
            if (it.id == chapterId) it.copy(isDownloaded = false) else it
        }
        loadDownloadedChapters()
    }
}
