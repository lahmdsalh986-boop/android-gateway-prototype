package com.example.androidgateway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class GatewayService : Service() {
    private var proxy: ConnectProxy? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopGateway(); stopSelf(); return START_NOT_STICKY
        }
        val serverHost = intent?.getStringExtra(EXTRA_SERVER_HOST)?.trim().orEmpty()
        val serverPort = intent?.getIntExtra(EXTRA_SERVER_PORT, 9000) ?: 9000
        val proxyPort = intent?.getIntExtra(EXTRA_PROXY_PORT, 8080) ?: 8080
        createChannel(); startForeground(NOTIFICATION_ID, GatewayNotification.create(this, "Gateway proxy is active"))
        if (proxy == null) {
            try {
                GatewayController.reset(proxyPort)
                proxy = ConnectProxy(this, proxyPort, serverHost, serverPort).also { it.start() }
            } catch (e: Exception) {
                GatewayController.setError("Unable to start gateway: ${e.message}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { stopGateway(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopGateway() { proxy?.close(); proxy = null; GatewayController.stop() }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Gateway service", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val ACTION_STOP = "com.example.androidgateway.STOP"
        const val EXTRA_SERVER_HOST = "serverHost"
        const val EXTRA_SERVER_PORT = "serverPort"
        const val EXTRA_PROXY_PORT = "proxyPort"
        const val CHANNEL_ID = "gateway_service"
        const val NOTIFICATION_ID = 101
    }
}
