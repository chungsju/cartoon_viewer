package com.example.cartoon_viewer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cartoon_viewer.model.Manga
import com.example.cartoon_viewer.model.SubCategories
import com.example.cartoon_viewer.ui.MangaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaListScreen(
    categoryTitle: String,
    url: String,
    isSearch: Boolean = false,
    viewModel: MangaViewModel,
    onMangaClick: (Manga) -> Unit
) {
    val mangaList by viewModel.mangaList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isEndReached by viewModel.isEndReached.collectAsState()
    var selectedSca by remember { mutableStateOf("전체") }
    
    val gridState = rememberLazyGridState()
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= mangaList.size - 5 && !isLoading && !isEndReached && !isSearch
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMoreManga()
        }
    }

    LaunchedEffect(url, selectedSca) {
        if (isSearch) {
            viewModel.searchManga(url)
        } else {
            viewModel.loadMangaList(url, selectedSca)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (isSearch) "검색 결과: $url" else categoryTitle) })
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isSearch && (categoryTitle.contains("만화") || categoryTitle.contains("추천"))) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SubCategories) { sca ->
                        FilterChip(
                            selected = selectedSca == sca,
                            onClick = { selectedSca = sca },
                            label = { Text(sca) }
                        )
                    }
                }
            }

            if (isLoading && mangaList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!isLoading && mangaList.isEmpty() && isSearch) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "검색 결과가 없습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mangaList) { manga ->
                        MangaItem(manga = manga, onClick = { onMangaClick(manga) })
                    }
                    
                    if (isLoading && mangaList.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
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
}

@Composable
fun MangaItem(manga: Manga, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            AsyncImage(
                model = manga.thumbnailUrl,
                contentDescription = manga.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = manga.title,
                modifier = Modifier.padding(8.dp),
                maxLines = 2,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
