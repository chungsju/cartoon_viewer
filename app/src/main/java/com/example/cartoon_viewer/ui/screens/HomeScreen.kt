package com.example.cartoon_viewer.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.example.cartoon_viewer.model.MangaCategory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.example.cartoon_viewer.ui.MangaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MangaViewModel,
    onCategoryClick: (MangaCategory) -> Unit,
    onSearchClick: (String) -> Unit,
    onLibraryClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onLastReadClick: (String, String, String, String, String, String, Int) -> Unit,
    onZipFileClick: (String) -> Unit
) {
    val lastRead by viewModel.lastRead.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf(viewModel.getBaseUrl()) }
    val context = LocalContext.current

    val gso = remember {
        try {
            val clientId = context.getString(com.example.cartoon_viewer.R.string.default_web_client_id)
            if (clientId.contains(".apps.googleusercontent.com")) {
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(clientId)
                    .requestEmail()
                    .build()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("HomeScreen", "GSO initialization failed", e)
            null
        }
    }
    val googleSignInClient = remember(gso) { 
        try {
            gso?.let { GoogleSignIn.getClient(context, it) }
        } catch (e: Exception) {
            Log.e("HomeScreen", "GoogleSignInClient initialization failed", e)
            null
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                viewModel.signInWithGoogle(token)
            }
        } catch (e: ApiException) {
            Log.e("HomeScreen", "Google sign in failed", e)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLastRead()
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            if (inputStream != null) {
                val fileName = it.path?.substringAfterLast("/") ?: "local_zip"
                viewModel.loadZipFile(inputStream, fileName)
                onZipFileClick(fileName)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("https://contents.kyobobook.co.kr/resources/fo/images/common/ink/united/logo_book.svg")
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .height(40.dp)
                            .padding(vertical = 4.dp),
                        contentScale = ContentScale.Fit
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = {
                        if (userEmail == null) {
                            if (googleSignInClient != null) {
                                signInLauncher.launch(googleSignInClient.signInIntent)
                            } else {
                                Log.e("HomeScreen", "Google Sign In Client is NULL. Check Web Client ID.")
                            }
                        } else {
                            showLogoutDialog = true
                        }
                    }) {
                        Icon(
                            if (userEmail != null) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            contentDescription = "Cloud Sync",
                            tint = if (userEmail != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (userEmail != null) {
                        Text(
                            text = userEmail?.substringBefore("@") ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    IconButton(onClick = { 
                        newUrl = viewModel.getBaseUrl()
                        showUrlDialog = true 
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = { Text("어떤 만화를 찾으시나요?") },
                    trailingIcon = {
                        IconButton(onClick = { if (searchQuery.isNotEmpty()) onSearchClick(searchQuery) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotEmpty()) onSearchClick(searchQuery)
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        lastRead?.let { lr ->
                            LastReadButton(lr) {
                                onLastReadClick(lr.mangaId, lr.chapter.id, lr.chapter.title, lr.chapter.link, lr.mangaUrl, lr.mangaTitle, lr.pageIndex)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        Text(
                            text = "카테고리",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(MangaCategory.entries) { category ->
                        CategoryCard(category) { 
                            onCategoryClick(category) 
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        BookmarkButton(onBookmarkClick)
                    }

                    item {
                        ZipFileButton { zipPickerLauncher.launch("application/zip") }
                    }

                    item {
                        LibraryButton(onLibraryClick)
                    }
                }
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Base URL 설정") },
            text = {
                Column {
                    Text("사이트 주소가 변경된 경우 아래에 입력하세요.")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateBaseUrl(newUrl)
                    showUrlDialog = false
                }) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("클라우드 동기화") },
            text = { Text("동기화를 해제하고 로그아웃 하시겠습니까?\n현재 계정: $userEmail") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut()
                    showLogoutDialog = false
                }) {
                    Text("동기화 해제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.syncCloudData()
                    showLogoutDialog = false
                }) {
                    Text("지금 동기화")
                }
            }
        )
    }
}

@Composable
fun CategoryCard(category: MangaCategory, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
fun LibraryButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LibraryBooks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "내 보관함 (다운로드)",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
fun LastReadButton(lastRead: com.example.cartoon_viewer.model.BookmarkedChapter, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "마지막 본 페이지 이어보기",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${lastRead.mangaTitle} - ${lastRead.chapter.title} (${lastRead.pageIndex + 1}p)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun BookmarkButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD700) // Gold
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "내 책갈피 (즐겨찾기)",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
fun ZipFileButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FolderZip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "로컬 zip 파일",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}
