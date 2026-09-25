package com.example.androidgateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class LinkState { STOPPED, STARTING, READY, CONNECTED, DEGRADED, ERROR }

data class GatewaySnapshot(
    val gateway: LinkState = LinkState.STOPPED,
    val wifi: String = "UNKNOWN",
    val client: LinkState = LinkState.STOPPED,
    val dataTunnel: LinkState = LinkState.STOPPED,
    val control: LinkState = LinkState.STOPPED,
    val vpn: LinkState = LinkState.STOPPED,
    val wifiRx: Long = 0,
    val wifiTx: Long = 0,
    val tunnelRx: Long = 0,
    val tunnelTx: Long = 0,
    val vpnRx: Long = 0,
    val activeClients: Int = 0,
    val proxyPort: Int = 8080,
    val localHotspot: String = "OFF",
    val lastError: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)

object GatewayController {
    private val wifiRx = AtomicLong(0)
    private val wifiTx = AtomicLong(0)
    private val tunnelRx = AtomicLong(0)
    private val tunnelTx = AtomicLong(0)
    private val vpnRx = AtomicLong(0)
    private val listeners = CopyOnWriteArrayList<(GatewaySnapshot) -> Unit>()
    private val events = CopyOnWriteArrayList<String>()

    @Volatile private var gateway = LinkState.STOPPED
    @Volatile private var client = LinkState.STOPPED
    @Volatile private var tunnel = LinkState.STOPPED
    @Volatile private var control = LinkState.STOPPED
    @Volatile private var vpn = LinkState.STOPPED
    @Volatile private var activeClients = 0
    @Volatile private var activeTunnels = 0
    @Volatile private var proxyPort = 8080
    @Volatile private var localHotspot = "OFF"
    @Volatile private var error: String? = null

    fun reset(port: Int) {
        wifiRx.set(0); wifiTx.set(0); tunnelRx.set(0); tunnelTx.set(0); vpnRx.set(0)
        gateway = LinkState.STARTING; client = LinkState.STOPPED; tunnel = LinkState.READY
        control = LinkState.STOPPED; vpn = LinkState.STOPPED; activeClients = 0; activeTunnels = 0; proxyPort = port; error = null
        event("Gateway service is starting on TCP port $port")
        publish()
    }

    fun stop() {
        gateway = LinkState.STOPPED; client = LinkState.STOPPED; tunnel = LinkState.STOPPED
        control = LinkState.STOPPED; vpn = LinkState.STOPPED; activeClients = 0; activeTunnels = 0
        event("Gateway service stopped")
        publish()
    }

    fun setGateway(state: LinkState, message: String? = null) {
        gateway = state
        if (message != null) event(message)
        publish()
    }

    fun setTunnel(state: LinkState, message: String? = null) {
        tunnel = state
        if (message != null) event(message)
        publish()
    }

    fun tunnelOpened(sessionId: String) {
        activeTunnels += 1
        tunnel = LinkState.CONNECTED
        event("Data tunnel established: $sessionId")
        publish()
    }

    fun tunnelClosed(sessionId: String) {
        activeTunnels = (activeTunnels - 1).coerceAtLeast(0)
        tunnel = if (activeTunnels == 0 && gateway != LinkState.STOPPED) LinkState.READY else LinkState.CONNECTED
        event("Data tunnel closed: $sessionId")
        publish()
    }

    fun setControl(state: LinkState, message: String? = null) {
        control = state
        if (message != null) event(message)
        publish()
    }

    fun setVpn(state: LinkState, message: String? = null) {
        vpn = state
        if (message != null) event(message)
        publish()
    }

    fun clientOpened(peer: String) {
        activeClients += 1
        client = LinkState.CONNECTED
        event("Client proxy session opened: $peer")
        publish()
    }

    fun clientClosed(peer: String) {
        activeClients = (activeClients - 1).coerceAtLeast(0)
        client = if (activeClients == 0) LinkState.READY else LinkState.CONNECTED
        event("Client proxy session closed: $peer")
        publish()
    }

    fun addWifiRx(bytes: Long) { wifiRx.addAndGet(bytes); publish() }
    fun addWifiTx(bytes: Long) { wifiTx.addAndGet(bytes); publish() }
    fun addTunnelRx(bytes: Long) { tunnelRx.addAndGet(bytes); publish() }
    fun addTunnelTx(bytes: Long) { tunnelTx.addAndGet(bytes); publish() }
    fun addVpnRx(bytes: Long) { vpnRx.addAndGet(bytes); publish() }

    fun setLocalHotspot(value: String) { localHotspot = value; event("Local hotspot: $value"); publish() }
    fun setError(message: String) { error = message; gateway = LinkState.ERROR; event("ERROR: $message"); publish() }
    fun event(message: String) {
        events.add(0, "${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}  $message")
        while (events.size > 80) events.removeAt(events.lastIndex)
    }

    fun snapshot(context: Context): GatewaySnapshot = GatewaySnapshot(
        gateway = gateway,
        wifi = networkDescription(context),
        client = client,
        dataTunnel = tunnel,
        control = control,
        vpn = vpn,
        wifiRx = wifiRx.get(),
        wifiTx = wifiTx.get(),
        tunnelRx = tunnelRx.get(),
        tunnelTx = tunnelTx.get(),
        vpnRx = vpnRx.get(),
        activeClients = activeClients,
        proxyPort = proxyPort,
        localHotspot = localHotspot,
        lastError = error
    )

    fun eventLines(): List<String> = events.toList()
    fun subscribe(listener: (GatewaySnapshot) -> Unit) { listeners += listener }
    fun unsubscribe(listener: (GatewaySnapshot) -> Unit) { listeners -= listener }

    private fun publish() { listeners.forEach { it(snapshotOrNull()) } }
    private fun snapshotOrNull(): GatewaySnapshot = GatewaySnapshot(
        gateway = gateway, wifi = "Refresh screen for network state", client = client, dataTunnel = tunnel,
        control = control, vpn = vpn, wifiRx = wifiRx.get(), wifiTx = wifiTx.get(), tunnelRx = tunnelRx.get(),
        tunnelTx = tunnelTx.get(), vpnRx = vpnRx.get(), activeClients = activeClients, proxyPort = proxyPort, localHotspot = localHotspot,
        lastError = error
    )

    private fun networkDescription(context: Context): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "UNAVAILABLE"
        val network = cm.activeNetwork ?: return "NO ACTIVE UPSTREAM"
        val caps = cm.getNetworkCapabilities(network) ?: return "NO CAPABILITIES"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "UPSTREAM WI-FI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR (BLOCKED)"
            else -> "OTHER NETWORK (BLOCKED)"
        }
    }

    fun localIpv4Addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface -> iface.inetAddresses.toList().filterIsInstance<Inet4Address>() }
            .filter { !it.isLoopbackAddress && it.hostAddress != null }
            .map { it.hostAddress }
            .distinct()
    } catch (_: Exception) { emptyList() }
}
