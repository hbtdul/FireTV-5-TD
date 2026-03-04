
package de.firewebkiosk

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://example.com")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showDialog() {
        AlertDialog.Builder(this)
            .setTitle("Kiosk Einstellungen")
            .setMessage("Hier können später URL und Rotation eingestellt werden.")
            .setPositiveButton("OK", null)
            .show()
    }
}
