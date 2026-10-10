package com.livemagic.overlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val requestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 30, 28, 30)
            setBackgroundColor(0xFF10151F.toInt())
        }

        val title = TextView(this).apply {
            text = "LIVE MAGIC OVERLAY"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
        }

        val description = TextView(this).apply {
            text = "Floating profile card\nViewer join animations\nGift effects\n\nBackend: 127.0.0.1:3000"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFD7E1F2.toInt())
            setPadding(0, 24, 0, 24)
        }

        val start = Button(this).apply {
            text = "ENABLE FLOATING OVERLAY"
            setOnClickListener { enableOverlay() }
        }

        val stop = Button(this).apply {
            text = "STOP OVERLAY"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
                Toast.makeText(
                    this@MainActivity,
                    "Overlay stopped",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(start)
        layout.addView(stop)

        setContentView(layout)
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, requestCode)
        } else {
            startOverlay()
        }
    }

    private fun startOverlay() {
        val intent = Intent(this, OverlayService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        Toast.makeText(
            this,
            "Overlay started",
            Toast.LENGTH_SHORT
        ).show()
    }

    @Deprecated("Permission callback")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == this.requestCode) {
            if (Settings.canDrawOverlays(this)) {
                startOverlay()
            }
        }
    }
}
