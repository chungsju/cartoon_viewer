package com.example.cartoon_viewer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cartoon_viewer.model.Chapter
import com.example.cartoon_viewer.model.MangaPage
import com.example.cartoon_viewer.ui.MangaViewModel
import kotlinx.coroutines.launch
import java.io.File

enum class ViewMode(val displayName: String) {
    SINGLE("한장보기"), 
    SPREAD("두장보기"), 
    SPLIT("나눠보기")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    chapterTitle: String,
    mangaId: String,
    chapterId: String,
    chapterUrl: String,
    viewModel: MangaViewModel,
    onNextChapterClick: (Chapter) -> Unit
) {
    val pages by viewModel.pages.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var viewMode by remember { mutableStateOf(ViewMode.SINGLE) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showNextChapterDialog by remember { mutableStateOf<Chapter?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(chapterUrl) {
        viewModel.loadPages(chapterUrl, mangaId, chapterId)
    }

    fun checkNextChapter() {
        val currentIndex = chapters.indexOfFirst { it.id == chapterId }
        // For descending list (typical), next chapter is at currentIndex - 1
        if (currentIndex > 0) {
            showNextChapterDialog = chapters[currentIndex - 1]
        }
    }

    var pagerScrollEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = { Text(chapterTitle) },
                    actions = {
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            ViewMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.displayName) },
                                    onClick = {
                                        viewMode = mode
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        val pagerCountRaw = if (pages.isEmpty()) 0 else when (viewMode) {
            ViewMode.SINGLE -> pages.size
            ViewMode.SPREAD -> (pages.size + 1) / 2
            ViewMode.SPLIT -> pages.size * 2
        }
        val pagerCount = if (pagerCountRaw > 0) pagerCountRaw + 1 else 0
        val pagerState = rememberPagerState(pageCount = { pagerCount })

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (pages.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = pagerScrollEnabled
                ) { pageIndex ->
                    if (pageIndex == pagerCountRaw) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { checkNextChapter() },
                                        onLongPress = { if (isFullscreen) isFullscreen = false }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "마지막 페이지입니다.\n터치하거나 오른쪽으로 넘기면\n다음 회차를 확인합니다.",
                                textAlign = TextAlign.Center,
                                color = if (isFullscreen) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        ViewerPage(
                            index = pageIndex,
                            pages = pages,
                            mode = viewMode,
                            onTapLeft = {
                                if (pagerState.currentPage > 0) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                }
                            },
                            onTapRight = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                            onLongPress = {
                                if (isFullscreen) {
                                    isFullscreen = false
                                }
                            },
                            onZoomChange = { zoomed ->
                                pagerScrollEnabled = !zoomed
                            }
                        )
                    }
                }

                // Bottom Slider (Shown in normal mode) - Compact version
                if (!isFullscreen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayIndex = (pagerState.currentPage + 1).coerceAtMost(pagerCountRaw)
                            Text(
                                text = "$displayIndex / $pagerCountRaw",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Slider(
                            value = pagerState.currentPage.toFloat().coerceAtMost((pagerCountRaw - 1).toFloat()),
                            onValueChange = {
                                scope.launch { pagerState.scrollToPage(it.toInt()) }
                            },
                            valueRange = 0f..maxOf(0f, (pagerCount - 1).toFloat()),
                            steps = if (pagerCountRaw > 1) pagerCountRaw - 2 else 0,
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }
        }
    }

    if (showNextChapterDialog != null) {
        AlertDialog(
            onDismissRequest = { showNextChapterDialog = null },
            title = { Text("다음 회차 보기") },
            text = { Text("'${showNextChapterDialog?.title}'를 보시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onNextChapterClick(showNextChapterDialog!!)
                    showNextChapterDialog = null
                }) {
                    Text("네")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNextChapterDialog = null }) {
                    Text("아니오")
                }
            }
        )
    }
}

@Composable
fun ViewerPage(
    index: Int,
    pages: List<MangaPage>,
    mode: ViewMode,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onLongPress: () -> Unit,
    onZoomChange: (Boolean) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when index changes (page switch)
    LaunchedEffect(index) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        if (scale <= 1f) { // Only navigate when not zoomed
                            if (tapOffset.x < size.width / 3) {
                                onTapLeft()
                            } else if (tapOffset.x > size.width * 2 / 3) {
                                onTapRight()
                            }
                        }
                    },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    val maxX = (size.width * (newScale - 1) / 2)
                    val maxY = (size.height * (newScale - 1) / 2)
                    
                    scale = newScale
                    offset = Offset(
                        x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                        y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                    )
                    onZoomChange(scale > 1f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentAlignment = Alignment.Center
        ) {
            when (mode) {
                ViewMode.SINGLE -> {
                    AsyncImage(
                        model = if (pages[index].localPath != null) File(pages[index].localPath!!) else pages[index].imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                ViewMode.SPREAD -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val firstIdx = index * 2
                        val secondIdx = index * 2 + 1
                        
                        Box(modifier = Modifier.weight(1f)) {
                            if (firstIdx < pages.size) {
                                AsyncImage(
                                    model = if (pages[firstIdx].localPath != null) File(pages[firstIdx].localPath!!) else pages[firstIdx].imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (secondIdx < pages.size) {
                                AsyncImage(
                                    model = if (pages[secondIdx].localPath != null) File(pages[secondIdx].localPath!!) else pages[secondIdx].imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                ViewMode.SPLIT -> {
                    val pageIdx = index / 2
                    val isRightHalf = index % 2 == 1 
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = if (pages[pageIdx].localPath != null) File(pages[pageIdx].localPath!!) else pages[pageIdx].imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 2f
                                    translationX = if (isRightHalf) -size.width / 2 else size.width / 2
                                },
                            contentScale = ContentScale.FillHeight
                        )
                    }
                }
            }
        }
    }
}
