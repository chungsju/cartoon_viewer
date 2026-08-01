package com.example.cartoon_viewer.network

import android.content.Context
import android.util.Log
import com.example.cartoon_viewer.model.Chapter
import com.example.cartoon_viewer.model.Manga
import com.example.cartoon_viewer.model.MangaPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class SpotvScraper(context: Context) {
    private val TAG = "SpotvScraper"
    private val urlProvider = UrlProvider(context)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .header("Referer", urlProvider.baseUrl)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun fetchMangaList(url: String): List<Manga> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching manga list: $url")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            if (html.isEmpty()) return@withContext emptyList()

            val doc = Jsoup.parse(html, url)
            val mangaList = mutableListOf<Manga>()
            // Use a more generic selector to include items without is-card class
            val items = doc.select("li[data-id]")

            for (item in items) {
                val a = item.select("a").first() ?: continue
                val link = a.attr("abs:href")
                val id = item.attr("data-id").ifEmpty {
                    link.split("is=").lastOrNull()?.split("&")?.first() ?: ""
                }
                val title = item.select(".homelist-title span, .subject").text().trim().ifEmpty { 
                    item.attr("data-title") 
                }
                var thumb = item.select(".homelist-thumb").attr("data-mobile-image")
                if (thumb.isEmpty()) {
                    val style = item.select(".homelist-thumb").attr("style")
                    thumb = "url\\(['\"]?(.+?)['\"]?\\)".toRegex().find(style)?.groupValues?.get(1) ?: ""
                }
                if (thumb.isEmpty()) {
                    thumb = item.select("img").attr("abs:src")
                }
                if (thumb.startsWith("//")) {
                    thumb = "https:$thumb"
                }

                if (id.isNotEmpty() && title.isNotEmpty()) {
                    mangaList.add(Manga(id, title, thumb, link))
                }
            }
            
            if (mangaList.isEmpty()) {
                doc.select("a[href*=is=]").forEach { a ->
                    val link = a.attr("abs:href")
                    val title = a.text().trim()
                    val id = link.split("is=").lastOrNull()?.split("&")?.first() ?: ""
                    if (title.length > 1 && id.isNotEmpty() && !mangaList.any { it.id == id }) {
                        mangaList.add(Manga(id, title, "", link))
                    }
                }
            }
            
            Log.d(TAG, "Parsed ${mangaList.size} manga items")
            mangaList
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga list: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchChapters(mangaUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching chapters: $mangaUrl")
            val request = Request.Builder().url(mangaUrl).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            if (html.isEmpty()) return@withContext emptyList()
            
            val doc = Jsoup.parse(html, mangaUrl)
            val chapters = mutableListOf<Chapter>()
            val buttons = doc.select("#comic-episode-list li button.episode")

            buttons.forEach { btn ->
                val onClick = btn.attr("onclick")
                var link = ""
                val match = "location\\.href\\s*=\\s*[`\"'](.+?)[`\"']".toRegex().find(onClick)
                val rawLink = match?.groupValues?.get(1) ?: ""
                
                if (rawLink.isNotEmpty()) {
                    link = if (rawLink.startsWith("./")) {
                        val baseUrl = mangaUrl.substringBefore("/bbs/") + "/bbs/"
                        baseUrl + rawLink.substring(2)
                    } else if (rawLink.startsWith("http")) {
                        rawLink
                    } else {
                        val baseUrl = mangaUrl.substringBefore("/bbs/") + "/bbs/"
                        baseUrl + rawLink
                    }
                }

                val title = btn.select(".episode-title").text().trim()
                val bannerStyle = btn.select(".episode-banner").attr("style")
                var thumb = "url\\(['\"]?(.+?)['\"]?\\)".toRegex().find(bannerStyle)?.groupValues?.get(1) ?: ""
                if (thumb.startsWith("//")) thumb = "https:$thumb"
                val dateText = btn.select(".free-date").text().split("(").first().trim()
                
                val id = link.split("wr_id=").lastOrNull()?.split("&")?.first() ?: ""
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    chapters.add(Chapter(id, title, link, thumb, dateText))
                }
            }

            if (chapters.isEmpty()) {
                doc.select("a[href*=wr_id=]").forEach { a ->
                    val link = a.attr("abs:href")
                    val title = a.text().trim()
                    val id = link.split("wr_id=").lastOrNull()?.split("&")?.first() ?: ""
                    if (id.isNotEmpty() && title.length > 1 && !chapters.any { it.id == id }) {
                        if (!title.contains("목록") && !title.contains("다음")) {
                            chapters.add(Chapter(id, title, link))
                        }
                    }
                }
            }
            
            Log.d(TAG, "Parsed ${chapters.size} chapters")
            chapters
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapters: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchPages(chapterUrl: String): List<MangaPage> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching pages: $chapterUrl")
            val request = Request.Builder().url(chapterUrl).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            if (html.isEmpty()) return@withContext emptyList()
            
            val pages = mutableListOf<MangaPage>()
            
            // 1. JS 변수 img_list 추출
            val imgListPattern = "var\\s+img_list\\s*=\\s*\\[(.+?)\\]".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = imgListPattern.find(html)
            
            if (match != null) {
                val arrayContent = match.groupValues[1]
                val urls = arrayContent.split(",").map { it.trim().trim('"', '\'') }
                urls.forEach { rawUrl ->
                    var src = rawUrl
                    if (src.isNotEmpty()) {
                        if (src.startsWith("//")) src = "https:$src"
                        if (!pages.any { it.imageUrl == src }) {
                            pages.add(MangaPage(src))
                        }
                    }
                }
            }
            
            if (pages.isEmpty()) {
                val doc = Jsoup.parse(html, chapterUrl)
                doc.select(".view-content img, #bo_v_atc img").forEach { img ->
                    var src = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                    if (src.startsWith("//")) src = "https:$src"
                    if (src.isNotEmpty() && !src.contains("ad_") && !pages.any { it.imageUrl == src }) {
                        pages.add(MangaPage(src))
                    }
                }
            }
            
            Log.d(TAG, "Parsed ${pages.size} pages")
            pages
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching pages: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchManga(query: String): List<Manga> = withContext(Dispatchers.IO) {
        val searchUrl = "${urlProvider.baseUrl}bbs/search_stx.php?stx=${java.net.URLEncoder.encode(query, "UTF-8")}"
        try {
            Log.d(TAG, "Searching manga: $searchUrl")
            val request = Request.Builder().url(searchUrl).build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            if (html.isEmpty()) return@withContext emptyList()

            val doc = Jsoup.parse(html, searchUrl)
            val mangaList = mutableListOf<Manga>()

            // 1. Try to use the toons_item structure (Search specific)
            val toonsItems = doc.select("li.toons_item")
            for (item in toonsItems) {
                val id = item.attr("data-id")
                val title = item.attr("data-title")
                val thumbStyle = item.select(".homelist-thumb").attr("style")
                var thumb = "url\\(['\"]?(.+?)['\"]?\\)".toRegex().find(thumbStyle)?.groupValues?.get(1) ?: ""
                if (thumb.startsWith("//")) thumb = "https:$thumb"
                
                // Construct the link as seen in the page's JS
                val link = "${urlProvider.baseUrl}bbs/board.php?bo_table=toons&stx=${java.net.URLEncoder.encode(title, "UTF-8")}&search=1&is=$id"
                
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    mangaList.add(Manga(id, title, thumb, link))
                }
            }

            // 2. Try to use the standard is-card structure if present (Home/Category style)
            if (mangaList.isEmpty()) {
                val items = doc.select("li.is-card")
                for (item in items) {
                    val a = item.select("a").first() ?: continue
                    val link = a.attr("abs:href")
                    val id = item.attr("data-id").ifEmpty {
                        link.split("is=").lastOrNull()?.split("&")?.first() ?: 
                        link.split("wr_id=").lastOrNull()?.split("&")?.first() ?: ""
                    }
                    val title = item.select(".homelist-title span, .subject").text().trim()
                    var thumb = item.select(".homelist-thumb").attr("data-mobile-image")
                    if (thumb.isEmpty()) {
                        thumb = item.select("img").attr("abs:src")
                    }
                    if (thumb.startsWith("//")) thumb = "https:$thumb"

                    if (id.isNotEmpty() && title.isNotEmpty()) {
                        mangaList.add(Manga(id, title, thumb, link))
                    }
                }
            }

            // 2. Fallback to general link search in the main content area
            if (mangaList.isEmpty()) {
                val mainContent = doc.select("#main, .wrap").first() ?: doc
                mainContent.select("a").forEach { a ->
                    val link = a.attr("abs:href")
                    val id = when {
                        link.contains("is=") -> link.substringAfter("is=").substringBefore("&")
                        link.contains("wr_id=") -> link.substringAfter("wr_id=").substringBefore("&")
                        else -> ""
                    }
                    
                    if (id.isNotEmpty() && !mangaList.any { it.id == id }) {
                        val title = a.text().trim()
                        // If the title contains the query or is reasonably long, it might be a result
                        if (title.length > 1 && !title.contains("목록") && !title.contains("다음") && !title.contains("이전")) {
                            // Try to find a thumbnail in the parent or siblings
                            var thumb = a.parent()?.select("img")?.first()?.attr("abs:src") ?: ""
                            if (thumb.isEmpty()) {
                                thumb = a.parent()?.parent()?.select("img")?.first()?.attr("abs:src") ?: ""
                            }
                            if (thumb.startsWith("//")) thumb = "https:$thumb"
                            
                            mangaList.add(Manga(id, title, thumb, link))
                        }
                    }
                }
            }
            
            // 3. One more fallback: if we found results but they are all missing thumbnails, 
            // maybe they are in a table and we should look for images differently.
            if (mangaList.isNotEmpty() && mangaList.all { it.thumbnailUrl.isEmpty() }) {
                // Try to find images in the same row if it's a table
                mangaList.forEachIndexed { index, manga ->
                    if (manga.thumbnailUrl.isEmpty()) {
                        val a = doc.select("a[href*=${manga.id}]").first()
                        val thumb = a?.closest("tr")?.select("img")?.first()?.attr("abs:src") ?: ""
                        if (thumb.isNotEmpty()) {
                            mangaList[index] = manga.copy(thumbnailUrl = if (thumb.startsWith("//")) "https:$thumb" else thumb)
                        }
                    }
                }
            }
            Log.d(TAG, "Search returned ${mangaList.size} items")
            mangaList
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchMoreManga(page: Int, sca: String, type: String): List<Manga> = withContext(Dispatchers.IO) {
        val url = "${urlProvider.baseUrl}bbs/ajax.gettoonlist.php"
        val formBody = okhttp3.FormBody.Builder()
            .add("page", page.toString())
            .add("sca", sca)
            .add("type", type)
            .build()
        
        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
            
        try {
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: ""
            val jsonObject = org.json.JSONObject(json)
            val list = jsonObject.getJSONArray("list")
            val mangaList = mutableListOf<Manga>()
            
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val id = item.getString("wr_id")
                val title = item.getString("wr_subject")
                val thumb = "${urlProvider.baseUrl}data/toon_category/$id.webp"
                val link = "${urlProvider.baseUrl}bbs/board.php?bo_table=toons&stx=${java.net.URLEncoder.encode(title, "UTF-8")}&is=$id"
                mangaList.add(Manga(id, title, thumb, link))
            }
            mangaList
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching more manga: ${e.message}")
            emptyList()
        }
    }
}
