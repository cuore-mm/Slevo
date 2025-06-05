package com.websarva.wings.android.bbsviewer.ui.common

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Use auto-mirrored icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppWebViewScreen(
    url: String,
    onDismiss: () -> Unit
) {
    var webViewTitle by remember { mutableStateOf("Loading...") }
    val context = LocalContext.current
    // var webView: WebView? = null // Not strictly needed to be stored if only used in factory

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(webViewTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(context).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                webViewTitle = "Loading..."
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                webViewTitle = view?.title ?: "Web Page"
                            }

                            // It's good practice to override shouldOverrideUrlLoading for security and control
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                url?.let { view?.loadUrl(it) }
                                return true // Indicates that the WebView handles the URL loading
                            }
                        }
                        settings.javaScriptEnabled = true
                        // Add other settings as needed, e.g.:
                        // settings.domStorageEnabled = true
                        // settings.useWideViewPort = true
                        // settings.loadWithOverviewMode = true
                        // settings.setSupportZoom(true)
                        // settings.builtInZoomControls = true
                        // settings.displayZoomControls = false
                        loadUrl(url)
                        // webView = this // Assign if needed for external control via update block
                    }
                },
                update = { webView ->
                    // This block is called when the composable is recomposed.
                    // If the URL could change dynamically AND you want to reload,
                    // you might need to do: if (webView.url != url) webView.loadUrl(url)
                    // However, for this specific use case where InAppWebViewScreen is recreated
                    // or url is a stable input for its lifetime, direct load in factory is often enough.
                }
            )
        }
    }
}
