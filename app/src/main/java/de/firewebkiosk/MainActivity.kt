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
        private const val PREF_ROT = "rotation"
        private const val DEFAULT_URL = "https://google.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Screen an lassen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        webView = findViewById(R.id.webView)

        // WebView
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

        applyRotation(rot)
        webView.loadUrl(normalizeUrl(url))
    }

    private fun normalizeUrl(input: String): String {
        val t = input.trim()
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://$t"
    }

    // Menü/Options Taste öffnet Settings
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_SETTINGS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_TOP_MENU
        ) {
            showSettingsDialog()
            return true
        }

        // Back: im WebView zurück
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
     * Dreht den kompletten Inhalt (Root-View) und sorgt dafür,
     * dass bei 90°/-90° die Breite/Höhe getauscht wird -> Vollbild statt “hochkant simuliert”.
     */
    private fun applyRotation(deg: Int) {
        root.post {
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels

            val lp = root.layoutParams

            if (deg == 90 || deg == -90) {
                lp.width = screenH
                lp.height = screenW
            } else {
                lp.width = screenW
                lp.height = screenH
            }

            root.layoutParams = lp
            root.pivotX = lp.width / 2f
            root.pivotY = lp.height / 2f
            root.rotation = deg.toFloat()
        }
    }
}
