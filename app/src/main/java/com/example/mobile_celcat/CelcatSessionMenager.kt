package com.example.mobile_celcat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient

class CelcatSessionManager(private val context: Context) {

    private val celcatUrl = "https://lodz.celcat.cloud/cal/"

    fun obtainSessionCookies(onCookieExtracted: (String) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            val handler = Handler(Looper.getMainLooper())
            var attempts = 0

            val checkCookieRunnable = object : Runnable {
                override fun run() {
                    CookieManager.getInstance().flush()
                    val cookies = CookieManager.getInstance().getCookie(celcatUrl) ?: ""

                    if (cookies.contains("ASP.NET_SessionId") || attempts > 10) {
                        onCookieExtracted(cookies)
                        webView.destroy()
                    } else {
                        attempts++
                        handler.postDelayed(this, 500)
                    }
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    handler.post(checkCookieRunnable)
                }
            }

            webView.loadUrl(celcatUrl)
        }
    }
}