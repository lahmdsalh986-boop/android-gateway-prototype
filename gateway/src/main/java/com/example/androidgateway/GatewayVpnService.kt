package com.example.androidgateway

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real user-consented TUN diagnostic. It intentionally installs only a narrow
 * test route and counts packets delivered to the VPN TUN. It does not claim to
 * receive tethered/Soft-AP client forwarding, which Android's public API does not guarantee.
 */
class GatewayVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(); stopSelf(); return START_NOT_STICKY }
        if (running.get()) return START_STICKY
        try {
            val builder = Builder()
                .setSession("Android Gateway TUN Diagnostic")
                .setMtu(1500)
                .addAddress("10.99.0.2", 32)
                .addRoute("10.99.0.0", 24)
            tun = builder.establish() ?: throw IOException("VpnService.Builder.establish returned null")
            running.set(true)
            GatewayController.setVpn(LinkState.CONNECTED, "TUN established: narrow diagnostic route 10.99.0.0/24")
            reader = Thread { readTun() }.apply { name = "gateway-tun-reader"; isDaemon = true; start() }
        } catch (e: Exception) {
            GatewayController.setVpn(LinkState.ERROR, "TUN blocked: ${e.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { stopVpn(); super.onDestroy() }

    private fun readTun() {
        try {
            FileInputStream(tun!!.fileDescriptor).use { input ->
                val packet = ByteArray(64 * 1024)
                while (running.get()) {
                    val count = input.read(packet)
                    if (count < 0) break
                    GatewayController.addVpnRx(count.toLong())
                    GatewayController.event("TUN packet observed: $count bytes")
                }
            }
        } catch (e: Exception) {
            if (running.get()) GatewayController.setVpn(LinkState.DEGRADED, "TUN reader stopped: ${e.message}")
        }
    }

    private fun stopVpn() {
        running.set(false)
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        reader?.interrupt(); reader = null
        GatewayController.setVpn(LinkState.STOPPED, "TUN diagnostic stopped")
    }

    companion object { const val ACTION_STOP = "com.example.androidgateway.STOP_VPN" }
}
