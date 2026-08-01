package com.example.cartoon_viewer.network

import android.content.Context
import android.content.SharedPreferences

class UrlProvider(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("url_settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "http://103.204.13.68:8904/") ?: "http://103.204.13.68:8904/"
        set(value) {
            val formatted = if (value.endsWith("/")) value else "$value/"
            prefs.edit().putString("base_url", formatted).apply()
        }

    fun getCategoryUrl(categoryTitle: String): String {
        val base = baseUrl
        return when (categoryTitle) {
            "완결만화" -> "${base}bbs/board.php?bo_table=toon_c&is_over=1&tablename=%EC%99%84%EA%B2%B0%EB%A7%8C%ED%99%94"
            "인기만화" -> "${base}bbs/board.php?bo_table=toon_c&tablename=%EC%9D%B8%EA%B8%B0%EB%A7%8C%ED%99%94"
            "최신만화" -> "${base}bbs/board.php?bo_table=toon_c&type=upd&tablename=%EC%B5%9C%EC%8B%A0%EB%A7%8C%ED%99%94"
            "매일 추천 100" -> "${base}bbs/board.php?bo_table=toon_c&type=today&tablename=%EB%A7%A4%EC%9D%BC%20%EC%B6%94%20100"
            else -> base
        }
    }
}
