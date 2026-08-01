package com.example.cartoon_viewer.network

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebViewScraper(private val context: Context) {
    
    suspend fun fetchHtml(url: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        val webView = WebView(context)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                android.util.Log.d("WebViewScraper", "Page finished loading: $url")
                // 약간의 지연을 주어 동적 컨텐츠 로딩 대기
                view?.postDelayed({
                    view.evaluateJavascript(
                        "(function() { return document.documentElement.outerHTML; })();",
                        object : ValueCallback<String> {
                            override fun onReceiveValue(value: String?) {
                                val html = value?.let {
                                    if (it.startsWith("\"") && it.endsWith("\"")) {
                                        it.substring(1, it.length - 1)
                                            .replace("\\u003C", "<")
                                            .replace("\\u003E", ">")
                                            .replace("\\\"", "\"")
                                            .replace("\\\\", "\\")
                                            .replace("\\n", "\n")
                                            .replace("\\r", "\r")
                                    } else it
                                } ?: ""
                                android.util.Log.d("WebViewScraper", "HTML length: ${html.length}")
                                if (!deferred.isCompleted) deferred.complete(html)
                                // webView.destroy() // 여기서 파괴하면 가끔 문제 발생
                            }
                        }
                    )
                }, 2000)
            }

            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                android.util.Log.e("WebViewScraper", "Error: $errorCode, $description, $failingUrl")
                if (!deferred.isCompleted) deferred.complete("")
            }
            
            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                android.util.Log.e("WebViewScraper", "HTTP Error: ${errorResponse?.statusCode}, ${errorResponse?.reasonPhrase}")
            }
        }
        
        webView.loadUrl(url)
        deferred.await()
    }
}
