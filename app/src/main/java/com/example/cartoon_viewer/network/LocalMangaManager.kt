package com.example.cartoon_viewer.network

import android.content.Context
import com.example.cartoon_viewer.model.Chapter
import com.example.cartoon_viewer.model.DownloadedChapter
import com.example.cartoon_viewer.model.MangaPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.net.URL

class LocalMangaManager(private val context: Context) {
    private val client = OkHttpClient()

    private fun getChapterDir(mangaId: String, chapterId: String): File {
        val dir = File(context.getExternalFilesDir(null), "downloads/$mangaId/$chapterId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isChapterDownloaded(mangaId: String, chapterId: String): Boolean {
        val dir = File(context.getExternalFilesDir(null), "downloads/$mangaId/$chapterId")
        val files = dir.listFiles() ?: return false
        return files.isNotEmpty() && files.any { it.extension.lowercase() == "jpg" || it.extension.lowercase() == "webp" }
    }

    suspend fun downloadChapter(mangaId: String, mangaTitle: String, chapter: Chapter, pages: List<MangaPage>): Boolean = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) return@withContext false
        
        val dir = getChapterDir(mangaId, chapter.id)
        
        // Save metadata
        val infoFile = File(dir, "info.txt")
        infoFile.writeText("$mangaTitle|${chapter.title}|${chapter.thumbnailUrl}|${chapter.date}")

        var downloadedCount = 0
        
        pages.forEachIndexed { index, page ->
            val ext = if (page.imageUrl.contains(".webp", ignoreCase = true)) "webp" else "jpg"
            val file = File(dir, String.format("%03d.$ext", index))
            
            if (file.exists() && file.length() > 0) {
                downloadedCount++
                return@forEachIndexed
            }

            val request = Request.Builder()
                .url(page.imageUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                .header("Referer", "http://103.204.13.68:8904/")
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            FileOutputStream(file).use { it.write(bytes) }
                            downloadedCount++
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        downloadedCount > 0 && downloadedCount == pages.size
    }

    fun deleteChapter(mangaId: String, chapterId: String) {
        val dir = getChapterDir(mangaId, chapterId)
        dir.deleteRecursively()
    }

    fun getLocalPages(mangaId: String, chapterId: String): List<MangaPage> {
        val dir = getChapterDir(mangaId, chapterId)
        return dir.listFiles()?.filter { it.extension != "txt" }?.sortedBy { it.name }?.map {
            MangaPage(imageUrl = "", localPath = it.absolutePath)
        } ?: emptyList()
    }

    suspend fun extractZip(inputStream: InputStream, title: String): List<MangaPage> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "zip_extract/$title")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        val extractedFiles = mutableListOf<Pair<String, String>>() // OriginalPath to LocalPath
        unzipRecursive(inputStream, tempDir, "", extractedFiles)
        
        extractedFiles.sortedBy { it.first }.map { 
            MangaPage(imageUrl = "", localPath = it.second)
        }
    }

    private fun unzipRecursive(inputStream: InputStream, targetDir: File, currentPath: String, files: MutableList<Pair<String, String>>) {
        ZipInputStream(inputStream).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val fullPath = if (currentPath.isEmpty()) entryName else "$currentPath/$entryName"
                
                if (!entry.isDirectory) {
                    if (isImageFile(entryName)) {
                        val safeName = fullPath.replace(File.separator, "_").replace("/", "_")
                        val localFile = File(targetDir, safeName)
                        FileOutputStream(localFile).use { out ->
                            zip.copyTo(out)
                        }
                        files.add(fullPath to localFile.absolutePath)
                    } else if (entryName.lowercase().endsWith(".zip")) {
                        val tempZip = File(targetDir, "temp_${System.currentTimeMillis()}_${entryName.substringAfterLast("/")}")
                        FileOutputStream(tempZip).use { out ->
                            zip.copyTo(out)
                        }
                        unzipRecursive(tempZip.inputStream(), targetDir, fullPath, files)
                        tempZip.delete()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun isImageFile(filename: String): Boolean {
        val ext = filename.lowercase()
        return ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") || ext.endsWith(".webp")
    }

    fun getAllDownloadedChapters(): List<DownloadedChapter> {
        val root = File(context.getExternalFilesDir(null), "downloads")
        if (!root.exists()) return emptyList()
        
        val result = mutableListOf<DownloadedChapter>()
        root.listFiles()?.forEach { mangaDir ->
            if (mangaDir.isDirectory) {
                val mangaId = mangaDir.name
                mangaDir.listFiles()?.forEach { chapterDir ->
                    if (chapterDir.isDirectory) {
                        val chapterId = chapterDir.name
                        val infoFile = File(chapterDir, "info.txt")
                        if (infoFile.exists()) {
                            val content = infoFile.readText().split("|")
                            if (content.size >= 4) {
                                result.add(DownloadedChapter(
                                    mangaId = mangaId,
                                    mangaTitle = content[0],
                                    chapter = Chapter(
                                        id = chapterId,
                                        title = content[1],
                                        link = "", // Not needed for local
                                        thumbnailUrl = content[2],
                                        date = content[3],
                                        isDownloaded = true
                                    )
                                ))
                            }
                        }
                    }
                }
            }
        }
        return result.sortedByDescending { it.chapter.date }
    }
}
