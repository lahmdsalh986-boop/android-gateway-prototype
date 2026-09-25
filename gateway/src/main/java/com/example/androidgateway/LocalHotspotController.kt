package com.example.androidgateway

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.annotation.TargetApi

/** Uses Android's official LocalOnlyHotspot API. It never claims to expose tethered packets. */
@TargetApi(26)
class LocalHotspotController(private val context: Context) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun start(onInfo: (String) -> Unit) {
        if (reservation != null) { onInfo("Local hotspot already active"); return }
        if (android.os.Build.VERSION.SDK_INT < 26) {
            val text = "BLOCKED BY ANDROID: LocalOnlyHotspot requires Android 8.0/API 26 or newer"
            GatewayController.setLocalHotspot(text); onInfo(text); return
        }
        GatewayController.setLocalHotspot("STARTING")
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = value
                    val config = value.wifiConfiguration
                    val info = "ACTIVE | SSID=${config?.SSID ?: "hidden"} | passphrase=${config?.preSharedKey ?: "unavailable"}"
                    GatewayController.setLocalHotspot(info)
                    onInfo(info)
                }
                override fun onStopped() {
                    reservation = null
                    GatewayController.setLocalHotspot("STOPPED")
                    onInfo("Local hotspot stopped")
                }
                override fun onFailed(reason: Int) {
                    reservation = null
                    val text = "FAILED (Android reason=$reason). Check location/nearby-Wi-Fi permission, Wi-Fi enabled, and device support."
                    GatewayController.setLocalHotspot(text)
                    onInfo(text)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            val text = "FAILED: missing runtime location or nearby-Wi-Fi permission"
            GatewayController.setLocalHotspot(text); onInfo(text)
        } catch (e: Exception) {
            val text = "FAILED: ${e.message}"
            GatewayController.setLocalHotspot(text); onInfo(text)
        }
    }

    fun stop() {
        try { reservation?.close() } catch (_: Exception) { }
        reservation = null
        GatewayController.setLocalHotspot("STOPPED")
    }
}
