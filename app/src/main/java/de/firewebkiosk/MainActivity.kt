package de.firewebkiosk

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        val prefs = getSharedPreferences("kiosk", Context.MODE_PRIVATE)
        val url = prefs.getString("url", "https://google.com")!!

        webView.loadUrl(url)

        applyRotation(prefs.getInt("rotation", 0))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun openSettings() {

        val prefs = getSharedPreferences("kiosk", Context.MODE_PRIVATE)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50,40,50,10)

        val urlInput = EditText(this)
        urlInput.hint = "URL"
        urlInput.setText(prefs.getString("url", ""))

        val rotationSpinner = Spinner(this)

        val rotations = arrayOf("0", "90", "180", "-90")

        rotationSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            rotations
        )

        val currentRotation = prefs.getInt("rotation",0).toString()
        rotationSpinner.setSelection(rotations.indexOf(currentRotation))

        layout.addView(urlInput)
        layout.addView(rotationSpinner)

        AlertDialog.Builder(this)
            .setTitle("Kiosk Einstellungen")
            .setView(layout)
            .setPositiveButton("Speichern") { _, _ ->

                val url = urlInput.text.toString()
                val rotation = rotationSpinner.selectedItem.toString().toInt()

                prefs.edit()
                    .putString("url", url)
                    .putInt("rotation", rotation)
                    .apply()

                webView.loadUrl(url)
                applyRotation(rotation)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun applyRotation(rotation: Int) {

        val display = windowManager.defaultDisplay

        val rotationValue = when(rotation) {
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            -90 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }

        requestedOrientation = when(rotationValue) {
            Surface.ROTATION_90 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            Surface.ROTATION_180 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            Surface.ROTATION_270 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
}
