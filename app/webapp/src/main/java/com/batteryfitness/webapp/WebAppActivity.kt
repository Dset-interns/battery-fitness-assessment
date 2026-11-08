package com.batteryfitness.webapp

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * Shows the web app from the APK's own assets, so the whole battery, pose tests included, works with no network.
 *
 * The page is served from https://appassets.androidplatform.net/ (a secure origin, so the camera and IndexedDB work)
 * by answering its requests from assets/www. Everything else, such as the sync server, goes to the network as usual.
 * Three things a browser does for a web page are done here: the camera permission, printing to PDF, and saving a file.
 */
class WebAppActivity : Activity() {

    private lateinit var web: WebView
    private var pendingCamera: PermissionRequest? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // an 800 m run is minutes of filming
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true                       // localStorage and IndexedDB hold the athletes, results and videos
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                serveAsset(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                request.url.host != HOST          // the app never navigates away from itself
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE !in request.resources) { request.deny(); return@runOnUiThread }
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    } else {
                        pendingCamera = request
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                    }
                }
            }

            // <input type="file" capture>: the photo of the scale display and the wall reference photo.
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE); true
                } catch (e: Exception) {
                    fileCallback = null; false
                }
            }
        }

        if (savedInstanceState == null) web.loadUrl("https://$HOST/index.html") else web.restoreState(savedInstanceState)
    }

    /** Answers a request for the app's own files from assets/www, with the types a module script and WebAssembly need. */
    private fun serveAsset(uri: Uri): WebResourceResponse? {
        if (uri.host != HOST) return null
        val path = uri.path.orEmpty().trimStart('/').ifEmpty { "index.html" }
        return try {
            val type = when (path.substringAfterLast('.', "").lowercase()) {
                "html" -> "text/html"
                "js", "mjs" -> "text/javascript"
                "json" -> "application/json"
                "wasm" -> "application/wasm"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "svg" -> "image/svg+xml"
                else -> "application/octet-stream"
            }
            WebResourceResponse(type, if (type.startsWith("text/") || type == "application/json") "utf-8" else null,
                200, "OK", mapOf("Cache-Control" to "no-cache"), assets.open("www/$path"))
        } catch (e: FileNotFoundException) {
            WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
    }

    /** What the page can ask the phone to do. The page checks that this object exists before it uses it. */
    inner class Bridge {
        /** Opens the system print screen for the report; "Save as PDF" there makes the file. */
        @JavascriptInterface
        fun print() {
            runOnUiThread {
                val pm = getSystemService(PRINT_SERVICE) as PrintManager
                pm.print("Battery Fitness report", web.createPrintDocumentAdapter("Battery_Fitness_report"),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }

        /** Saves a file (a video, the scale photo, the consent PDF) to the Downloads folder. */
        @JavascriptInterface
        fun saveFile(name: String, mime: String, base64: String): Boolean = try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                put(MediaStore.Downloads.MIME_TYPE, mime.ifEmpty { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            contentResolver.openOutputStream(uri)!!.use { it.write(Base64.decode(base64, Base64.DEFAULT)) }
            runOnUiThread { Toast.makeText(this@WebAppActivity, "Saved to Downloads: $name", Toast.LENGTH_LONG).show() }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code != REQ_CAMERA) return
        val req = pendingCamera ?: return
        pendingCamera = null
        if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) req.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else req.deny()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    // The web app pushes its own history entries (sheets, screens), so the back button walks them before leaving.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); super.onPause() }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private companion object {
        const val HOST = "appassets.androidplatform.net"
        const val REQ_CAMERA = 1
        const val REQ_FILE = 2
    }
}
