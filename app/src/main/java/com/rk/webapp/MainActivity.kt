package com.rk.webapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var pendingGeo: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var lastBackPressedAt: Long = 0L

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        val data = result.data
        val uris = if (result.resultCode == RESULT_OK && data != null) {
            if (data.clipData != null) Array(data.clipData!!.itemCount) { i -> data.clipData!!.getItemAt(i).uri }
            else data.data?.let { arrayOf(it) }
        } else null
        callback.onReceiveValue(uris)
        fileCallback = null
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val request = pendingWebPermission
        if (request != null) {
            val allowed = request.resources.filter { resource ->
                when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> result[Manifest.permission.CAMERA] == true || hasPermission(Manifest.permission.CAMERA)
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> result[Manifest.permission.RECORD_AUDIO] == true || hasPermission(Manifest.permission.RECORD_AUDIO)
                    else -> false
                }
            }.toTypedArray()
            if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
            pendingWebPermission = null
        }
        pendingGeo?.invoke(pendingGeoOrigin, hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION), false)
        pendingGeo = null
        pendingGeoOrigin = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 25, 100)
        window.navigationBarColor = android.graphics.Color.BLACK
        webView = WebView(this)
        setContentView(webView)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setGeolocationEnabled(true)
            allowFileAccess = false
            allowContentAccess = true
            // WebRTC calls may need audio/video playback to start after the user taps Call.
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        updateNetworkCacheMode()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme ?: return true
                if (scheme == "https" || scheme == "http") return false
                return try { startActivity(Intent(Intent.ACTION_VIEW, uri)); true } catch (_: Exception) { true }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try { filePicker.launch(params.createIntent()); true } catch (_: Exception) { fileCallback = null; false }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    // Only allow camera/microphone capture for the secure website loaded by this app.
                    val origin = request.origin
                    val trustedOrigin = origin.scheme == "https" &&
                        (origin.host == "keen-sprinkles-1c71ec.netlify.app" || origin.host == "trycloudflare.com" || origin.host?.endsWith(".trycloudflare.com") == true)
                    if (!trustedOrigin) {
                        request.deny()
                        return@runOnUiThread
                    }

                    val supportedResources = request.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }.toTypedArray()
                    if (supportedResources.isEmpty()) {
                        request.deny()
                        return@runOnUiThread
                    }

                    val needed = mutableListOf<String>()
                    if (supportedResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && !hasPermission(Manifest.permission.CAMERA)) {
                        needed += Manifest.permission.CAMERA
                    }
                    if (supportedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        needed += Manifest.permission.RECORD_AUDIO
                    }

                    if (needed.isEmpty()) {
                        request.grant(supportedResources)
                    } else {
                        pendingWebPermission?.deny()
                        pendingWebPermission = request
                        permissionLauncher.launch(needed.toTypedArray())
                    }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val alreadyAllowed = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (alreadyAllowed) callback.invoke(origin, true, false)
                else {
                    pendingGeo = callback
                    pendingGeoOrigin = origin
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl("https://keen-sprinkles-1c71ec.netlify.app/")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressedAt < 2000L) finish()
                    else {
                        lastBackPressedAt = now
                        Toast.makeText(this@MainActivity, "Tekan kembali sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun updateNetworkCacheMode() {
        if (!::webView.isInitialized) return
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        webView.settings.cacheMode = if (online) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) updateNetworkCacheMode()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }
}
