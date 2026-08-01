package com.example.cartoon_viewer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cartoon_viewer.ui.MangaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MangaViewModel,
    onChapterClick: (String, String, String, String) -> Unit 
) {
    val downloadedChapters by viewModel.downloadedChapters.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDownloadedChapters()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("보관함") })
        }
    ) { padding ->
        if (downloadedChapters.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("다운로드된 회차가 없습니다.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(downloadedChapters) { item ->
                    ListItem(
                        headlineContent = { Text(item.chapter.title) },
                        supportingContent = { Text("${item.mangaTitle} • ${item.chapter.date}") },
                        leadingContent = {
                            AsyncImage(
                                model = item.chapter.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp, 60.dp),
                                contentScale = ContentScale.Crop
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteChapter(item.mangaId, item.chapter.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            onChapterClick(item.mangaId, item.chapter.id, item.chapter.title, "")
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
