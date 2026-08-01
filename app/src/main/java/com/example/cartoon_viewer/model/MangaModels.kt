package com.example.cartoon_viewer.model

data class Manga(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val link: String,
    val category: String = ""
)

data class Chapter(
    val id: String,
    val title: String,
    val link: String,
    val thumbnailUrl: String = "",
    val date: String = "",
    val isDownloaded: Boolean = false
)

data class MangaPage(
    val imageUrl: String,
    val localPath: String? = null
)

data class DownloadedChapter(
    val mangaId: String,
    val mangaTitle: String,
    val chapter: Chapter
)

enum class MangaCategory(val title: String, val url: String) {
    COMPLETED("완결만화", "http://103.204.13.68:8904/bbs/board.php?bo_table=toon_c&is_over=1&tablename=%EC%99%84%EA%B2%B0%EB%A7%8C%ED%99%94"),
    POPULAR("인기만화", "http://103.204.13.68:8904/bbs/board.php?bo_table=toon_c&tablename=%EC%9D%B8%EA%B8%B0%EB%A7%8C%ED%99%94"),
    LATEST("최신만화", "http://103.204.13.68:8904/bbs/board.php?bo_table=toon_c&type=upd&tablename=%EC%B5%9C%EC%8B%A0%EB%A7%8C%ED%99%94"),
    RECOMMENDED("매일 추천 100", "http://103.204.13.68:8904/bbs/board.php?bo_table=toon_c&type=today&tablename=%EB%A7%A4%EC%9D%BC%20%EC%B6%94%EC%B2%9C%20100")
}

val SubCategories = listOf(
    "전체", "BL", "러브코미디", "17", "판타지", "순정", "드라마", "학원", "게임", "SF",
    "스릴러", "먹방", "TS", "스포츠", "이세계", "추리", "일상", "라노벨", "백합", "시대",
    "애니화", "전생", "붕탁", "무협", "호러", "공포"
)
