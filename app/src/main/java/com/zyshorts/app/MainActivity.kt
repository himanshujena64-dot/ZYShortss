package com.zyshorts.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    // The live Hugging Face Space that powers ZYShorts.
    // Changing your Space or moving hosts later? Just update this one line
    // and rebuild — no other code changes needed.
    private val appUrl = "https://himanshujena564-newtrail.hf.space"

    private lateinit var webView: WebView
    private lateinit var errorView: View

    // Holds the pending callback while the system file picker is open.
    // Must be registered here (as a field), not inside onCreate, per
    // ActivityResultLauncher's lifecycle requirements.
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && data != null) {
            val clipData = data.clipData
            if (clipData != null) {
                // multiple files selected
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else {
                data.data?.let { arrayOf(it) }
            }
        } else {
            null
        }
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        errorView = findViewById(R.id.error_view)
        val retryButton = findViewById<View>(R.id.retry_button)

        webView.overScrollMode = View.OVER_SCROLL_NEVER

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = false
        settings.useWideViewPort = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.offscreenPreRaster = true
        settings.allowFileAccess = true

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: ""
                return if (host.contains("hf.space") || host.contains("huggingface.co")) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (e: Exception) {
                        // no browser available, ignore
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                errorView.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    webView.visibility = View.GONE
                    errorView.visibility = View.VISIBLE
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                Toast.makeText(
                    this@MainActivity,
                    "Secure connection could not be verified.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // This is the actual fix: without a WebChromeClient overriding
        // onShowFileChooser, tapping any <input type="file"> (like the
        // Excel uploader) inside the WebView does nothing at all — Android
        // never opens a picker on its own the way a real browser does.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val intent = fileChooserParams.createIntent()
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileChooserCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        "Couldn't open file picker.",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }
        }

        // Same category of fix as onShowFileChooser above: a WebView doesn't
        // save files on its own the way a real browser does. st.download_button
        // in Streamlit triggers a "data:" URI download — without this listener,
        // tapping any download button in the app (template, videos, zips,
        // anything) silently does nothing.
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            if (url.startsWith("data:")) {
                saveDataUriToDownloads(url, contentDisposition, mimeType)
            } else {
                // Regular http(s) download link — hand off to the system
                // Download Manager as a fallback for any non-data-URI case.
                try {
                    val request = android.app.DownloadManager.Request(Uri.parse(url))
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    request.setNotificationVisibility(
                        android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    val dm = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
                    dm.enqueue(request)
                    Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        retryButton.setOnClickListener {
            errorView.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(appUrl)
        }

        webView.loadUrl(appUrl)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Decodes a base64 "data:" URI (what st.download_button generates) and
     * saves it into the device's Downloads folder, then notifies the user.
     */
    private fun saveDataUriToDownloads(dataUri: String, contentDisposition: String?, mimeType: String?) {
        try {
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex == -1) {
                Toast.makeText(this, "Download failed: invalid file data.", Toast.LENGTH_LONG).show()
                return
            }
            val meta = dataUri.substring(5, commaIndex) // strips "data:"
            val base64Payload = dataUri.substring(commaIndex + 1)
            val bytes = Base64.decode(base64Payload, Base64.DEFAULT)

            val resolvedMimeType = mimeType
                ?: meta.substringBefore(";").ifBlank { "application/octet-stream" }

            var fileName = URLUtil.guessFileName(dataUri, contentDisposition, resolvedMimeType)
            if (fileName.isNullOrBlank() || fileName == "downloadfile") {
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedMimeType) ?: "bin"
                fileName = "zyshorts_download.$ext"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, resolvedMimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (itemUri == null) {
                    Toast.makeText(this, "Couldn't save file.", Toast.LENGTH_LONG).show()
                    return
                }
                resolver.openOutputStream(itemUri)?.use { out: OutputStream -> out.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val outFile = java.io.File(downloadsDir, fileName)
                outFile.outputStream().use { it.write(bytes) }
            }

            Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
