package com.example.cartoon_viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cartoon_viewer.ui.MangaViewModel
import com.example.cartoon_viewer.ui.screens.*
import com.example.cartoon_viewer.ui.theme.Cartoon_ViewerTheme
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Cartoon_ViewerTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val viewModel: MangaViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onCategoryClick = { category ->
                    val dynamicUrl = viewModel.getCategoryUrl(category.title)
                    val encodedUrl = URLEncoder.encode(dynamicUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("list/${category.title}/$encodedUrl")
                },
                onSearchClick = { query ->
                    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                    navController.navigate("search/$encodedQuery")
                },
                onLibraryClick = {
                    navController.navigate("library")
                },
                onBookmarkClick = {
                    navController.navigate("bookmarks")
                },
                onLastReadClick = { mangaId, chapterId, title, url, mangaUrl, mangaTitle, pageIndex ->
                    val encodedManga = URLEncoder.encode(mangaTitle, StandardCharsets.UTF_8.toString())
                    val encodedChapter = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    navController.navigate("viewer/$encodedManga/$encodedChapter/$mangaId/$chapterId/$encodedUrl/$pageIndex")
                },
                onZipFileClick = { fileName ->
                    val encodedManga = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    val encodedChapter = URLEncoder.encode("전체", StandardCharsets.UTF_8.toString())
                    navController.navigate("viewer/$encodedManga/$encodedChapter/local/local/local_zip/0")
                }
            )
        }
        composable("library") {
            LibraryScreen(
                viewModel = viewModel,
                onChapterClick = { mangaId, chapterId, title, url, mangaTitle ->
                    val encodedManga = URLEncoder.encode(mangaTitle, StandardCharsets.UTF_8.toString())
                    val encodedChapter = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                    val encodedUrl = if (url.isNotEmpty()) URLEncoder.encode(url, StandardCharsets.UTF_8.toString()) else "local"
                    navController.navigate("viewer/$encodedManga/$encodedChapter/$mangaId/$chapterId/$encodedUrl/0")
                }
            )
        }
        composable("bookmarks") {
            BookmarkScreen(
                viewModel = viewModel,
                onChapterClick = { mangaId, chapterId, title, url, mangaUrl, mangaTitle, pageIndex ->
                    val encodedManga = URLEncoder.encode(mangaTitle, StandardCharsets.UTF_8.toString())
                    val encodedChapter = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                    val encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8.toString())
                    navController.navigate("viewer/$encodedManga/$encodedChapter/$mangaId/$chapterId/$encodedUrl/$pageIndex")
                }
            )
        }
        composable(
            "search/{query}",
            arguments = listOf(
                navArgument("query") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val query = URLDecoder.decode(backStackEntry.arguments?.getString("query") ?: "", StandardCharsets.UTF_8.toString())
            MangaListScreen(
                categoryTitle = "검색: $query",
                url = query,
                isSearch = true,
                viewModel = viewModel,
                onMangaClick = { manga ->
                    val encodedUrl = URLEncoder.encode(manga.link, StandardCharsets.UTF_8.toString())
                    navController.navigate("detail/${manga.title}/${manga.id}/$encodedUrl")
                }
            )
        }
        composable(
            "list/{categoryTitle}/{url}",
            arguments = listOf(
                navArgument("categoryTitle") { type = NavType.StringType },
                navArgument("url") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("categoryTitle") ?: ""
            // Navigation already decodes the argument once.
            // If I encoded it twice, I need to decode it once? 
            // Actually, let's just use it as is if it looks like a URL.
            val rawUrl = backStackEntry.arguments?.getString("url") ?: ""
            val url = if (rawUrl.startsWith("http")) rawUrl else URLDecoder.decode(rawUrl, StandardCharsets.UTF_8.toString())
            
            MangaListScreen(
                categoryTitle = title,
                url = url,
                viewModel = viewModel,
                onMangaClick = { manga ->
                    val encodedTitle = URLEncoder.encode(manga.title, StandardCharsets.UTF_8.toString())
                    val encodedUrl = URLEncoder.encode(manga.link, StandardCharsets.UTF_8.toString())
                    navController.navigate("detail/$encodedTitle/${manga.id}/$encodedUrl")
                }
            )
        }
        composable(
            "detail/{title}/{mangaId}/{mangaUrl}",
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("mangaId") { type = NavType.StringType },
                navArgument("mangaUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())
            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
            val mangaUrl = URLDecoder.decode(backStackEntry.arguments?.getString("mangaUrl") ?: "", StandardCharsets.UTF_8.toString())
            MangaDetailScreen(
                title = title,
                mangaId = mangaId,
                mangaUrl = mangaUrl,
                viewModel = viewModel,
                onChapterClick = { chapter ->
                    val encodedManga = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                    val encodedChapter = URLEncoder.encode(chapter.title, StandardCharsets.UTF_8.toString())
                    val encodedUrl = URLEncoder.encode(chapter.link, StandardCharsets.UTF_8.toString())
                    navController.navigate("viewer/$encodedManga/$encodedChapter/$mangaId/${chapter.id}/$encodedUrl/0")
                },
                onFirstChapterClick = { firstUrl ->
                    // To handle "first chapter click", we need a chapter object.
                    // But we can also just find it in the list if available, or fetch it.
                    // Actually, the simplest way is to let the viewer handle the URL directly if we can.
                    // Or, find the chapter with this URL in the current list.
                    val firstChapter = viewModel.chapters.value.find { it.link == firstUrl }
                    if (firstChapter != null) {
                        val encodedManga = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        val encodedChapter = URLEncoder.encode(firstChapter.title, StandardCharsets.UTF_8.toString())
                        val encodedUrl = URLEncoder.encode(firstChapter.link, StandardCharsets.UTF_8.toString())
                        navController.navigate("viewer/$encodedManga/$encodedChapter/$mangaId/${firstChapter.id}/$encodedUrl/0")
                    } else {
                        // If not in list (paged), we might need to fetch or just use a placeholder
                        val encodedManga = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                        val encodedChapter = URLEncoder.encode("1화", StandardCharsets.UTF_8.toString())
                        val encodedUrl = URLEncoder.encode(firstUrl, StandardCharsets.UTF_8.toString())
                        val wrId = firstUrl.substringAfter("wr_id=").substringBefore("&")
                        navController.navigate("viewer/$encodedManga/$encodedChapter/$mangaId/$wrId/$encodedUrl/0")
                    }
                }
            )
        }
        composable(
            "viewer/{mangaTitle}/{chapterTitle}/{mangaId}/{chapterId}/{chapterUrl}/{pageIndex}",
            arguments = listOf(
                navArgument("mangaTitle") { type = NavType.StringType },
                navArgument("chapterTitle") { type = NavType.StringType },
                navArgument("mangaId") { type = NavType.StringType },
                navArgument("chapterId") { type = NavType.StringType },
                navArgument("chapterUrl") { type = NavType.StringType },
                navArgument("pageIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val mTitle = URLDecoder.decode(backStackEntry.arguments?.getString("mangaTitle") ?: "", StandardCharsets.UTF_8.toString())
            val cTitle = URLDecoder.decode(backStackEntry.arguments?.getString("chapterTitle") ?: "", StandardCharsets.UTF_8.toString())
            val mangaId = backStackEntry.arguments?.getString("mangaId") ?: ""
            val chapterId = backStackEntry.arguments?.getString("chapterId") ?: ""
            val url = URLDecoder.decode(backStackEntry.arguments?.getString("chapterUrl") ?: "", StandardCharsets.UTF_8.toString())
            val pageIdx = backStackEntry.arguments?.getInt("pageIndex") ?: 0
            
            ViewerScreen(
                chapterTitle = cTitle,
                mangaTitle = mTitle,
                mangaId = mangaId,
                chapterId = chapterId,
                chapterUrl = url,
                initialPageIndex = pageIdx,
                viewModel = viewModel,
                onNextChapterClick = { chapter ->
                    val encodedManga = URLEncoder.encode(mTitle, StandardCharsets.UTF_8.toString())
                    val encodedChapter = URLEncoder.encode(chapter.title, StandardCharsets.UTF_8.toString())
                    val encodedUrl = URLEncoder.encode(chapter.link, StandardCharsets.UTF_8.toString())
                    navController.navigate("viewer/$encodedManga/$encodedChapter/$mangaId/${chapter.id}/$encodedUrl/0") {
                        popUpTo("viewer/{mangaTitle}/{chapterTitle}/{mangaId}/{chapterId}/{chapterUrl}/{pageIndex}") { inclusive = true }
                    }
                }
            )
        }
    }
}
