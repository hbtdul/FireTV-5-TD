// app/src/main/java/de/firewebkiosk/MainActivity.kt
package de.firewebkiosk

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout

    private val prefs by lazy { getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE) }

    companion object {
        private const val PREF_URL = "url"
        private const val PREF_ROT = "rotation_deg"
        private const val DEFAULT_URL = "https://google.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)

        setupWebView()

        val url = prefs.getString(PREF_URL, DEFAULT_URL) ?: DEFAULT_URL
        val rot = prefs.getInt(PREF_ROT, 0)

        applyRotation(rot)
        webView.loadUrl(normalizeUrl(url))
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullscreen()
    }

    /**
     * Captures FireTV "Options/Menu" more reliably than only onKeyDown().
     * Also adds a fallback: long-press DPAD_CENTER to open settings on remotes
     * where MENU is intercepted by the system.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (isSettingsKey(event.keyCode)) {
                showSettingsDialog()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            showSettingsDialog()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun isSettingsKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_SETTINGS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_TOP_MENU
    }

    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus(View.FOCUS_DOWN)
    }

    private fun normalizeUrl(input: String): String {
        val t = input.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://$t"
    }

    private fun showSettingsDialog() {
        val urlInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString(PREF_URL, DEFAULT_URL) ?: DEFAULT_URL)
            setSelection(text.length)
            hint = "https://dein-link.de"
        }

        val rotValues = intArrayOf(0, 90, 180, -90)
        val rotLabels = arrayOf("0°", "90°", "180°", "-90°")
        val currentRot = prefs.getInt(PREF_ROT, 0)
        val checkedIndex = rotValues.indexOf(currentRot).let { if (it >= 0) it else 0 }

        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            rotLabels.forEachIndexed { idx, label ->
                addView(
                    RadioButton(this@MainActivity).apply {
                        id = View.generateViewId()
                        text = label
                        isChecked = idx == checkedIndex
                    }
                )
            }
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)

            addView(TextView(this@MainActivity).apply { text = "URL" })
            addView(urlInput)

            addView(TextView(this@MainActivity).apply {
                text = "\nMonitor-Rotation"
            })
            addView(radioGroup)
        }

        AlertDialog.Builder(this)
            .setTitle("Kiosk Einstellungen")
            .setView(container)
            .setPositiveButton("Speichern") { _, _ ->
                val urlRaw = urlInput.text?.toString().orEmpty().trim()
                val pickedIndex = (0 until radioGroup.childCount).firstOrNull { i ->
                    (radioGroup.getChildAt(i) as? RadioButton)?.isChecked == true
                } ?: checkedIndex

                val rot = rotValues[pickedIndex]
                val url = normalizeUrl(if (urlRaw.isBlank()) DEFAULT_URL else urlRaw)

                prefs.edit()
                    .putString(PREF_URL, url)
                    .putInt(PREF_ROT, rot)
                    .apply()

                applyRotation(rot)
                webView.loadUrl(url)
                enterImmersiveFullscreen()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /**
     * Rotation + Vollbild-Füllung (Cover).
     *
     * Wir drehen nur die WebView (nicht den Root), damit Dialoge normal bleiben.
     * - Bei 90/-90 werden die Maße getauscht
     * - Dann skalieren wir mit "cover" (maxOf), damit der Screen immer voll ist
     * - Danach zentrieren wir per translationX/Y
     */
    private fun applyRotation(deg: Int) {
    webView.post {
        val parentW = root.width
        val parentH = root.height
        if (parentW == 0 || parentH == 0) return@post

        // Always fill parent BEFORE rotation
        val lp = webView.layoutParams as FrameLayout.LayoutParams
        lp.width = FrameLayout.LayoutParams.MATCH_PARENT
        lp.height = FrameLayout.LayoutParams.MATCH_PARENT
        webView.layoutParams = lp

        val w = parentW.toFloat()
        val h = parentH.toFloat()

        webView.pivotX = w / 2f
        webView.pivotY = h / 2f
        webView.rotation = deg.toFloat()

        // Bounding box after rotation (90/-90 swaps)
        val rotatedW = if (deg == 90 || deg == -90) h else w
        val rotatedH = if (deg == 90 || deg == -90) w else h

        // COVER: always full screen (no bars), may crop a bit
        val scale = maxOf(parentW / rotatedW, parentH / rotatedH)

        webView.scaleX = scale
        webView.scaleY = scale

        // Center the rotated+scaled bounding box inside parent
        webView.translationX = (parentW - rotatedW * scale) / 2f
        webView.translationY = (parentH - rotatedH * scale) / 2f
    }
}

    private fun enterImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
