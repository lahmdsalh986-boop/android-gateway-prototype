package com.example.androidgateway

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.app.PendingIntent

object GatewayNotification {
    fun create(context: Context, text: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, GatewayService.CHANNEL_ID)
            .setContentTitle("Android Gateway Prototype")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }
}
