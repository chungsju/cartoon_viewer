package com.example.cartoon_viewer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
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

enum class ReadingDirection(val displayName: String) {
    LTR("왼쪽부터 읽기"),
    RTL("오른쪽부터 읽기")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    chapterTitle: String,
    mangaTitle: String = "",
    mangaId: String,
    chapterId: String,
    chapterUrl: String,
    mangaUrl: String = "",
    initialPageIndex: Int = 0,
    viewModel: MangaViewModel,
    onNextChapterClick: (Chapter) -> Unit
) {
    val pages by viewModel.pages.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val viewMode by viewModel.viewMode.collectAsState()
    val readingDirection by viewModel.readingDirection.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    
    var showMenu by remember { mutableStateOf(false) }
    var showNextChapterDialog by remember { mutableStateOf<Chapter?>(null) }
    val scope = rememberCoroutineScope()

    var currentImageIndex by rememberSaveable { mutableIntStateOf(initialPageIndex) }
    val bookmarkedChapters by viewModel.bookmarkedChapters.collectAsState()
    
    val isBookmarked = remember(chapterId, bookmarkedChapters) {
        viewModel.isBookmarked(mangaId, chapterId)
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadBookmarkedChapters()
    }

    LaunchedEffect(chapterUrl) {
        viewModel.loadPages(chapterUrl, mangaId, chapterId)
    }

    // Auto-save last read
    LaunchedEffect(currentImageIndex, pages) {
        if (pages.isNotEmpty() && currentImageIndex < pages.size) {
            viewModel.saveLastRead(mangaId, mangaTitle, mangaUrl, Chapter(chapterId, chapterTitle, chapterUrl, thumbnailUrl = pages.firstOrNull()?.imageUrl ?: ""), currentImageIndex)
        }
    }

    fun checkNextChapter() {
        val currentIndex = chapters.indexOfFirst { it.id == chapterId }
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
                        IconButton(onClick = { 
                            viewModel.toggleBookmark(mangaId, mangaTitle, mangaUrl, Chapter(chapterId, chapterTitle, chapterUrl, thumbnailUrl = pages.firstOrNull()?.imageUrl ?: ""), currentImageIndex)
                        }) {
                            Icon(
                                if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) Color(0xFFFFD700) else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { viewModel.updateFullscreen(true) }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Full Screen")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            ViewMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = viewMode == mode, onClick = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(mode.displayName)
                                        }
                                    },
                                    onClick = {
                                        viewModel.updateViewMode(mode)
                                        showMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            ReadingDirection.values().forEach { dir ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = readingDirection == dir, onClick = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(dir.displayName) 
                                        }
                                    },
                                    onClick = {
                                        viewModel.updateReadingDirection(dir)
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

        LaunchedEffect(viewMode) {
            val newPagerIndex = when (viewMode) {
                ViewMode.SINGLE -> currentImageIndex
                ViewMode.SPREAD -> currentImageIndex / 2
                ViewMode.SPLIT -> currentImageIndex * 2
            }
            if (newPagerIndex < pagerCountRaw && pagerState.currentPage != newPagerIndex) {
                pagerState.scrollToPage(newPagerIndex)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage < pagerCountRaw) {
                currentImageIndex = when (viewMode) {
                    ViewMode.SINGLE -> pagerState.currentPage
                    ViewMode.SPREAD -> pagerState.currentPage * 2
                    ViewMode.SPLIT -> pagerState.currentPage / 2
                }
            }
        }

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
                                        onLongPress = { if (isFullscreen) viewModel.updateFullscreen(false) }
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
                            readingDirection = readingDirection,
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
                                    viewModel.updateFullscreen(false)
                                }
                            },
                            onZoomChange = { zoomed ->
                                pagerScrollEnabled = !zoomed
                            }
                        )
                    }
                }

                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Row(
                            modifier = Modifier.background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                             IconButton(onClick = { 
                                viewModel.toggleBookmark(mangaId, mangaTitle, mangaUrl, Chapter(chapterId, chapterTitle, chapterUrl, thumbnailUrl = pages.firstOrNull()?.imageUrl ?: ""), currentImageIndex)
                            }) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Bookmark",
                                    tint = if (isBookmarked) Color(0xFFFFD700) else Color.Black
                                )
                            }
                            IconButton(onClick = { viewModel.updateFullscreen(false) }) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.Black)
                            }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Black)
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    ViewMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = viewMode == mode, onClick = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(mode.displayName)
                                                }
                                            },
                                            onClick = {
                                                viewModel.updateViewMode(mode)
                                                showMenu = false
                                            }
                                        )
                                    }
                                    HorizontalDivider()
                                    ReadingDirection.values().forEach { dir ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(selected = readingDirection == dir, onClick = null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(dir.displayName) 
                                        }
                                    },
                                    onClick = {
                                        viewModel.updateReadingDirection(dir)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

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
    readingDirection: ReadingDirection = ReadingDirection.LTR,
    onTapLeft: () -> Unit,
    onTapRight: () -> Unit,
    onLongPress: () -> Unit,
    onZoomChange: (Boolean) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

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
                        if (scale <= 1f) {
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
                        
                        val leftPageIdx = if (readingDirection == ReadingDirection.LTR) firstIdx else secondIdx
                        val rightPageIdx = if (readingDirection == ReadingDirection.LTR) secondIdx else firstIdx

                        Box(modifier = Modifier.weight(1f)) {
                            if (leftPageIdx < pages.size) {
                                AsyncImage(
                                    model = if (pages[leftPageIdx].localPath != null) File(pages[leftPageIdx].localPath!!) else pages[leftPageIdx].imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (rightPageIdx < pages.size) {
                                AsyncImage(
                                    model = if (pages[rightPageIdx].localPath != null) File(pages[rightPageIdx].localPath!!) else pages[rightPageIdx].imageUrl,
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
                    val isSecondHalf = index % 2 == 1 
                    val showRightSide = if (readingDirection == ReadingDirection.LTR) isSecondHalf else !isSecondHalf
                    var imageSize by remember { mutableStateOf<IntSize?>(null) }
                    
                    Box(
                        modifier = Modifier.fillMaxSize().clipToBounds(), 
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = if (pages[pageIdx].localPath != null) File(pages[pageIdx].localPath!!) else pages[pageIdx].imageUrl,
                            contentDescription = null,
                            onState = { state ->
                                if (state is AsyncImagePainter.State.Success) {
                                    imageSize = IntSize(
                                        state.painter.intrinsicSize.width.toInt(),
                                        state.painter.intrinsicSize.height.toInt()
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    imageSize?.let { src ->
                                        val w = src.width.toFloat()
                                        val h = src.height.toFloat()
                                        val sw = size.width
                                        val sh = size.height
                                        
                                        val f = minOf(sw / w, sh / h)
                                        val totalScale = minOf(sw / (w / 2f), sh / h)
                                        val k = totalScale / f

                                        scaleX = k
                                        scaleY = k
                                        
                                        val visualWidth = w * f * k
                                        translationX = if (showRightSide) -visualWidth / 4f else visualWidth / 4f
                                    } ?: run {
                                        scaleX = 2f
                                        scaleY = 2f
                                        translationX = if (showRightSide) -size.width / 2f else size.width / 2f
                                    }
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
