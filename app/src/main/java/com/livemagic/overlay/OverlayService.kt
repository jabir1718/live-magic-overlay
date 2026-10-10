package com.livemagic.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import io.socket.client.IO
import io.socket.client.Socket

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var cardView: View? = null
    private var socket: Socket? = null

    private val executor = Executors.newSingleThreadExecutor()

    private var avatarView: ImageView? = null
    private var nameView: TextView? = null
    private var eventView: TextView? = null

    private var popupView: TextView? = null
    private val mainHandler = android.os.Handler(
        android.os.Looper.getMainLooper()
    )

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(1001, createNotification())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            stopSelf()
            return
        }

        showOverlay()
        connectBackend()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "live_magic_overlay",
                "LIVE Magic Overlay",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "live_magic_overlay")
                .setContentTitle("LIVE Magic Overlay")
                .setContentText("Floating overlay is running")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("LIVE Magic Overlay")
                .setContentText("Floating overlay is running")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        }
    }

    private fun showOverlay() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            background = roundedBackground(Color.WHITE, 22)
            elevation = 12f
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        avatarView = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(0xFFE6EAF0.toInt(), 50)
            clipToOutline = true
        }

        val avatarParams = LinearLayout.LayoutParams(48, 48)
        header.addView(avatarView, avatarParams)

        val userColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 0, 4, 0)
        }

        nameView = TextView(this).apply {
            text = "Waiting for LIVE user..."
            textSize = 15f
            setTextColor(0xFF172033.toInt())
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }

        val status = TextView(this).apply {
            text = "● LIVE MAGIC"
            textSize = 11f
            setTextColor(0xFF16A36A.toInt())
        }

        userColumn.addView(nameView)
        userColumn.addView(status)

        header.addView(
            userColumn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val close = TextView(this).apply {
            text = "×"
            textSize = 28f
            setTextColor(0xFF555D6B.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 0, 4, 0)
            setOnClickListener { stopSelf() }
        }

        header.addView(
            close,
            LinearLayout.LayoutParams(-2, -2)
        )

        eventView =
