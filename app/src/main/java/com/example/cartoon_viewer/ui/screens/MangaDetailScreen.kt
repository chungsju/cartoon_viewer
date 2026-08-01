package com.example.cartoon_viewer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.cartoon_viewer.model.Chapter
import com.example.cartoon_viewer.ui.MangaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaDetailScreen(
    title: String,
    mangaId: String,
    mangaUrl: String,
    viewModel: MangaViewModel,
    onChapterClick: (Chapter) -> Unit
) {
    val chapters by viewModel.chapters.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val isChaptersEndReached by viewModel.isChaptersEndReached.collectAsState()

    val listState = rememberLazyListState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= chapters.size - 5 && !isLoading && !isChaptersEndReached
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreChapters()
        }
    }

    LaunchedEffect(mangaUrl) {
        viewModel.loadChapters(mangaUrl, mangaId)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) })
        }
    ) { padding ->
        if (isLoading && chapters.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(chapters) { chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title) },
                        supportingContent = { Text(chapter.date) },
                        leadingContent = {
                            AsyncImage(
                                model = chapter.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp, 60.dp),
                                contentScale = ContentScale.Crop
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isDownloading[chapter.id] == true) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else if (chapter.isDownloaded) {
                                    IconButton(onClick = { /* Already downloaded */ }) {
                                        Icon(
                                            Icons.Default.DownloadDone,
                                            contentDescription = "Downloaded",
                                            tint = Color.Blue
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteChapter(mangaId, chapter.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red
                                        )
                                    }
                                } else {
                                    IconButton(onClick = { viewModel.downloadChapter(mangaId, title, chapter) }) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Download"
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { onChapterClick(chapter) }
                    )
                    HorizontalDivider()
                }
                
                if (isLoading && chapters.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
