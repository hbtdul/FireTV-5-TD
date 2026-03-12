// app/src/main/java/de/firewebkiosk/MainActivity.kt
package de.firewebkiosk

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
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
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var root: FrameLayout

    private val prefs by lazy { getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE) }

    companion object {
        private const val PREF_URL = "url"
        private const val PREF_ROT = "rotation_deg"
        private const val PREF_ZOOM = "zoom_percent"

        private const val DEFAULT_URL = "https://google.com"
        private const val DEFAULT_ZOOM = 100
        private const val MIN_ZOOM = 50
        private const val MAX_ZOOM = 300
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
        val zoom = prefs.getInt(PREF_ZOOM, DEFAULT_ZOOM)

        applyTransform(rot, zoom)
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
        s.setSupportZoom(false)

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

        val zoomInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(3))
            setText(prefs.getInt(PREF_ZOOM, DEFAULT_ZOOM).toString())
            setSelection(text.length)
            hint = "z.B. 120"
        }

        val rotValues = intArrayOf(0, 90, 180, -90)
        val rotLabels = arrayOf("0° (Horizontal)", "90° (Hochkant)", "180° (Horizontal)", "-90° (Hochkant)")
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

            addView(TextView(this@MainActivity).apply { text = "\nRotation" })
            addView(radioGroup)

            addView(TextView(this@MainActivity).apply { text = "\nZoom (%)  (50–300)" })
            addView(zoomInput)

            addView(TextView(this@MainActivity).apply {
                text = "\nTipp: Wenn MENU nicht geht → Long-Press OK öffnet dieses Menü."
            })
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

                val zoomParsed = zoomInput.text?.toString()?.trim()?.toIntOrNull() ?: DEFAULT_ZOOM
                val zoom = clampZoom(zoomParsed)

                prefs.edit()
                    .putString(PREF_URL, url)
                    .putInt(PREF_ROT, rot)
                    .putInt(PREF_ZOOM, zoom)
                    .apply()

                applyTransform(rot, zoom)
                webView.loadUrl(url)
                enterImmersiveFullscreen()
                webView.requestFocus(View.FOCUS_DOWN)
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                webView.requestFocus(View.FOCUS_DOWN)
            }
            .show()
    }

    private fun clampZoom(value: Int): Int = max(MIN_ZOOM, min(MAX_ZOOM, value))

    /**
     * Always FULLSCREEN (COVER) in landscape + portrait.
     * Adds user zoom (%) on top of cover-scale.
     */
    private fun applyTransform(rotationDeg: Int, zoomPercent: Int) {
        webView.post {
            val parentW = root.width
            val parentH = root.height
            if (parentW == 0 || parentH == 0) return@post

            val lp = webView.layoutParams as FrameLayout.LayoutParams
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            webView.layoutParams = lp

            val w = parentW.toFloat()
            val h = parentH.toFloat()

            webView.pivotX = w / 2f
            webView.pivotY = h / 2f
            webView.rotation = rotationDeg.toFloat()

            val rotatedW = if (rotationDeg == 90 || rotationDeg == -90) h else w
            val rotatedH = if (rotationDeg == 90 || rotationDeg == -90) w else h

            val coverScale = max(parentW / rotatedW, parentH / rotatedH)
            val userScale = clampZoom(zoomPercent) / 100f
            val scale = coverScale * userScale

            webView.scaleX = scale
            webView.scaleY = scale

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
