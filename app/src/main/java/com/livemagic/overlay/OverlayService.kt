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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var cardView: View? = null
    private var socket: Socket? = null

    private var nameView: TextView? = null
    private var eventView: TextView? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForeground(1001, createNotification())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)
        ) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show()
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

        val avatar = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundedBackground(0xFFE6EAF0.toInt(), 50)
            clipToOutline = true
        }

        header.addView(avatar, LinearLayout.LayoutParams(48, 48))

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

        header.addView(close, LinearLayout.LayoutParams(-2, -2))
        root.addView(header)

        eventView = TextView(this).apply {
            text = "Overlay ready"
            textSize = 13f
            setTextColor(0xFF344054.toInt())
            setPadding(4, 12, 4, 2)
        }

        root.addView(eventView)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 180
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()

                    try {
                        windowManager.updateViewLayout(root, params)
                    } catch (_: Exception) {
                    }

                    true
                }

                else -> false
            }
        }

        try {
            windowManager.addView(root, params)
            cardView = root
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not display overlay: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun connectBackend() {
        try {
            val options = IO.Options.builder()
                .setReconnection(true)
                .build()

            val client = IO.socket("http://127.0.0.1:3000", options)
            socket = client

            client.on(Socket.EVENT_CONNECT) {
                updateEvent("Backend connected")
            }

            client.on(Socket.EVENT_CONNECT_ERROR) {
                updateEvent("Backend unavailable")
            }

            client.on("profile") { args ->
                val data = args.firstOrNull()

                if (data is JSONObject) {
                    val username = data.optString("username", "LIVE user")
                    mainHandler.post {
                        nameView?.text = username
                        eventView?.text = "Profile updated"
                    }
                }
            }

            client.on("viewer_join") { args ->
                val data = args.firstOrNull()
                val username = if (data is JSONObject) {
                    data.optString("username", "A viewer")
                } else {
                    "A viewer"
                }

                updateEvent("$username joined")
            }

            client.on("gift") { args ->
                val data = args.firstOrNull()
                val giftName = if (data is JSONObject) {
                    data.optString("giftName", "gift")
                } else {
                    "gift"
                }

                updateEvent("Gift event: $giftName")
            }

            client.connect()
        } catch (_: Exception) {
            updateEvent("Backend unavailable")
        }
    }

    private fun updateEvent(message: String) {
        mainHandler.post {
            eventView?.text = message
        }
    }

    override fun onDestroy() {
        try {
            socket?.disconnect()
            socket?.off()
            socket?.close()
            socket = null
        } catch (_: Exception) {
        }

        try {
            cardView?.let { view ->
                if (view.isAttachedToWindow) {
                    windowManager.removeView(view)
                }
            }
        } catch (_: Exception) {
        }

        cardView = null
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
