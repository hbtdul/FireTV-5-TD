package de.firewebkiosk

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
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

        // Display anlassen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)

        // WebView Setup
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

        // gespeicherte Werte
        val url = prefs.getString(PREF_URL, DEFAULT_URL) ?: DEFAULT_URL
        val rot = prefs.getInt(PREF_ROT, 0)

        // Rotation anwenden + URL laden
        applyRotation(rot)
        webView.loadUrl(normalizeUrl(url))
    }

    private fun normalizeUrl(input: String): String {
        val t = input.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://$t"
    }

    // FireTV Remote:
    // - MENU / SETTINGS / TOP_MENU => Settings Dialog
    // - BACK => WebView zurück
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_SETTINGS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_TOP_MENU
        ) {
            showSettingsDialog()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun showSettingsDialog() {
        val urlInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString(PREF_URL, DEFAULT_URL) ?: DEFAULT_URL)
            setSelection(text.length)
            hint = "https://dein-link.de"
        }

        val rotLabels = arrayOf("0°", "90°", "180°", "-90°")
        val rotValues = intArrayOf(0, 90, 180, -90)
        val current = prefs.getInt(PREF_ROT, 0)
        val checkedIndex = rotValues.indexOf(current).let { if (it >= 0) it else 0 }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)

            addView(TextView(this@MainActivity).apply {
                text = "URL"
                textSize = 16f
            })
            addView(urlInput)

            addView(TextView(this@MainActivity).apply {
                text = "\nMonitor-Rotation"
                textSize = 16f
            })
        }

        AlertDialog.Builder(this)
            .setTitle("Kiosk Einstellungen")
            .setView(container)
            .setSingleChoiceItems(rotLabels, checkedIndex, null)
            .setPositiveButton("Speichern") { dialog, _ ->
                val urlRaw = urlInput.text?.toString().orEmpty().trim()

                val listView = (dialog as AlertDialog).listView
                val picked = listView.checkedItemPosition.takeIf { it >= 0 } ?: checkedIndex
                val rot = rotValues[picked]

                val url = normalizeUrl(if (urlRaw.isBlank()) DEFAULT_URL else urlRaw)

                prefs.edit()
                    .putString(PREF_URL, url)
                    .putInt(PREF_ROT, rot)
                    .apply()

                applyRotation(rot)
                webView.loadUrl(url)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    /**
     * Rotation + Vollbild-Füllung (Cover).
     *
     * Wichtig: Wir drehen nur die WebView (nicht den Root),
     * damit Dialoge normal bleiben.
     *
     * - Bei 90/-90 werden die Maße getauscht
     * - Dann skalieren wir mit "cover" (maxOf), damit der Screen immer voll ist
     * - Danach zentrieren wir per translationX/Y
     */
    private fun applyRotation(deg: Int) {
        webView.post {
            val rootW = root.width
            val rootH = root.height
            if (rootW == 0 || rootH == 0) return@post

            val contentW = if (deg == 90 || deg == -90) rootH else rootW
            val contentH = if (deg == 90 || deg == -90) rootW else rootH

            // Größe setzen
            val lp = webView.layoutParams as FrameLayout.LayoutParams
            lp.width = contentW
            lp.height = contentH
            webView.layoutParams = lp

            // Pivot Mitte
            webView.pivotX = contentW / 2f
            webView.pivotY = contentH / 2f

            // Rotation
            webView.rotation = deg.toFloat()

            // Cover-Scale (füllt immer komplett)
            val scale = maxOf(
                rootW.toFloat() / contentW.toFloat(),
                rootH.toFloat() / contentH.toFloat()
            )
            webView.scaleX = scale
            webView.scaleY = scale

            // Zentrieren
            webView.translationX = (rootW - contentW * scale) / 2f
            webView.translationY = (rootH - contentH * scale) / 2f
        }
    }
}
