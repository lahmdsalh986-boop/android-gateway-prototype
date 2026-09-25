package com.example.androidgateway

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.net.VpnService
import android.net.wifi.WifiManager
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var serverHost: EditText
    private lateinit var serverPort: EditText
    private lateinit var proxyPort: EditText
    private lateinit var status: TextView
    private lateinit var events: TextView
    private lateinit var capabilities: TextView
    private lateinit var startButton: Button
    private lateinit var hotspot: LocalHotspotController
    private var control: ControlChannel? = null
    private var lastRenderedTraffic = 0L
    private val listener: (GatewaySnapshot) -> Unit = { runOnUiThread { render() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hotspot = LocalHotspotController(this)
        setContentView(buildUi())
        requestNeededPermissions()
    }

    override fun onStart() { super.onStart(); GatewayController.subscribe(listener); render() }
    override fun onStop() { GatewayController.unsubscribe(listener); super.onStop() }
    override fun onDestroy() { control?.close(); super.onDestroy() }

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(24)) }
        scroll.addView(root)
        root.addView(title("Android Gateway Prototype", 24))
        root.addView(note("Real initial proof only: explicit HTTP CONNECT proxy → app-owned TCP data tunnel → test server. No claim of transparent tethered-packet interception."))
        root.addView(label("Test server endpoint (reachable via Wi-Fi/Ethernet only)"))
        serverHost = edit("Server DNS name or IP", "")
        serverPort = edit("Data tunnel TCP port", "9000", true)
        proxyPort = edit("Gateway proxy TCP port", "8080", true)
        root.addView(serverHost); root.addView(serverPort); root.addView(proxyPort)
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        startButton = Button(this).apply { text = "Start gateway"; setOnClickListener { toggleGateway() } }
        val hotspotButton = Button(this).apply { text = "Start local hotspot"; setOnClickListener { hotspot.start { toast(it); render() } } }
        actionRow.addView(startButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actionRow.addView(hotspotButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(actionRow)
        val controlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controlRow.addView(Button(this).apply { text = "Start control"; setOnClickListener { startControl() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        controlRow.addView(Button(this).apply { text = "Stop hotspot"; setOnClickListener { hotspot.stop(); render() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controlRow)
        val vpnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        vpnRow.addView(Button(this).apply { text = "Start TUN diagnostic"; setOnClickListener { startTunDiagnostic() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        vpnRow.addView(Button(this).apply { text = "Stop TUN"; setOnClickListener { stopTunDiagnostic() } }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(vpnRow)
        root.addView(Button(this).apply { text = "Run real Gateway → Server 1 MiB test"; setOnClickListener { runGatewaySelfTest() } })
        root.addView(title("Gateway Status", 19))
        status = TextView(this).apply { setTextColor(Color.rgb(28, 28, 28)); textSize = 14f; setPadding(0, dp(8), 0, dp(8)); typeface = android.graphics.Typeface.MONOSPACE }
        root.addView(status)
        root.addView(title("Event log", 19))
        events = TextView(this).apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE; setTextColor(Color.DKGRAY) }
        root.addView(events)
        root.addView(title("قدرات الجهاز", 19))
        capabilities = TextView(this).apply { textSize = 13f; typeface = android.graphics.Typeface.MONOSPACE; setTextColor(Color.DKGRAY) }
        root.addView(capabilities)
        root.addView(Button(this).apply { text = "تحديث قدرات الجهاز والعملاء الحقيقيين"; setOnClickListener { render() } })
        root.addView(note("Client setup: connect to this phone’s Wi-Fi LAN or LocalOnlyHotspot, then use the shown gateway IPv4 address and proxy port in a proxy-aware client. Install Gateway Test Client for the 10 MiB bidirectional SHA-256 test."))
        return scroll
    }

    private fun toggleGateway() {
        val current = GatewayController.snapshot(this)
        if (current.gateway != LinkState.STOPPED && current.gateway != LinkState.ERROR) {
            stopService(Intent(this, GatewayService::class.java).setAction(GatewayService.ACTION_STOP)); control?.close(); control = null; render(); return
        }
        val host = serverHost.text.toString().trim()
        val dataPort = serverPort.text.toString().toIntOrNull()
        val localPort = proxyPort.text.toString().toIntOrNull()
        if (host.isBlank() || dataPort == null || localPort == null || dataPort !in 1..65535 || localPort !in 1..65535) { toast("Enter a valid test-server host and ports"); return }
        val intent = Intent(this, GatewayService::class.java).apply {
            putExtra(GatewayService.EXTRA_SERVER_HOST, host); putExtra(GatewayService.EXTRA_SERVER_PORT, dataPort); putExtra(GatewayService.EXTRA_PROXY_PORT, localPort)
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        render()
    }

    private fun startControl() {
        val host = serverHost.text.toString().trim(); val port = serverPort.text.toString().toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65534) { toast("Start with valid test server first"); return }
        control?.close(); control = ControlChannel(this, host, port + 1).also { it.start() }
    }

    private fun render() {
        val s = GatewayController.snapshot(this)
        val device = DeviceCapabilityProbe.read(this)
        startButton.text = if (s.gateway == LinkState.STOPPED || s.gateway == LinkState.ERROR) "Start gateway" else "Stop gateway"
        val addresses = GatewayController.localIpv4Addresses().ifEmpty { listOf("No IPv4 address detected") }.joinToString(", ")
        val wifiManager = getSystemService(WifiManager::class.java)
        val concurrency = if (android.os.Build.VERSION.SDK_INT >= 30) wifiManager?.isStaApConcurrencySupported?.toString() ?: "UNKNOWN" else "API<30"
        val totalTraffic = s.wifiRx + s.wifiTx + s.tunnelRx + s.tunnelTx + s.vpnRx
        val pulse = if (totalTraffic > lastRenderedTraffic) "● DATA MOVING" else "○ idle (no new bytes)"
        lastRenderedTraffic = totalTraffic
        val arp = if (device.arpClients.isEmpty()) "لا يوجد عميل ظاهر في /proc/net/arp" else device.arpClients.joinToString("\\n")
        status.text = "BRIXAR GATEWAY\\n\\nGateway:        ${s.gateway}\\nWi-Fi:          ${s.wifi}\\nSTA+AP support: $concurrency\\nClient sessions: ${s.client} (${s.activeClients} active)\\nARP clients:    ${device.arpClients.size}\\nData Tunnel:    ${s.dataTunnel}\\nControl:        ${s.control} (${s.controlLatencyMs} ms)\\nVPN/TUN:        ${s.vpn}\\nLocal Hotspot:  ${s.localHotspot}\\nProxy listener: 0.0.0.0:${s.proxyPort}\\nPhone IPv4:     $addresses\\n\\nPath monitor\\nClient → Wi-Fi → Gateway → Data Tunnel → Server\\n$ pulse\\n\\nTraffic (real bytes only)\\nWi-Fi RX:       ${human(s.wifiRx)}\\nWi-Fi TX:       ${human(s.wifiTx)}\\nTunnel RX:      ${human(s.tunnelRx)}\\nTunnel TX:      ${human(s.tunnelTx)}\\nTUN RX:         ${human(s.vpnRx)}\\n\\nTransparent routing: غير متاح لتطبيق Android عادي\\nالمسار المتاح حاليًا: Explicit Proxy" + (s.lastError?.let { "\\n\\nLast error: $it" } ?: "")
        events.text = GatewayController.eventLines().joinToString("\\n").ifBlank { "No events yet." }
        capabilities.text = "Android: ${device.android}\\nRoot: ${device.root}\\nSystem privileges: ${device.systemPrivileges}\\nVPN: ${device.vpn}\\nHotspot: ${device.hotspot}\\nTethering: ${device.tethering}\\nالتوجيه الشفاف لعملاء Hotspot: ${device.transparentClientRouting}\\n\\nعملاء ظاهرون فعليًا من ARP:\\n$arp\\n\\nأسباب وقيود:\\n${device.reasons.joinToString("\\n") { "- $it" }}"
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.NEARBY_WIFI_DEVICES
        if (android.os.Build.VERSION.SDK_INT <= 32) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_FINE_LOCATION
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed += Manifest.permission.POST_NOTIFICATIONS
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 200)
    }

    private fun startTunDiagnostic() {
        val intent = VpnService.prepare(this)
        if (intent != null) { startActivityForResult(intent, 301); return }
        startService(Intent(this, GatewayVpnService::class.java)); render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 301 && resultCode == RESULT_OK) startService(Intent(this, GatewayVpnService::class.java))
        render()
    }

    private fun stopTunDiagnostic() { stopService(Intent(this, GatewayVpnService::class.java).setAction(GatewayVpnService.ACTION_STOP)); render() }

    private fun runGatewaySelfTest() {
        val host = serverHost.text.toString().trim()
        val port = serverPort.text.toString().toIntOrNull()
        if (host.isBlank() || port == null || port !in 1..65535) { toast("Enter a valid test-server host and data port"); return }
        GatewaySelfTest.run(this, host, port) { result -> runOnUiThread { toast(result); render() } }
    }

    private fun title(value: String, size: Int) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(Color.rgb(10, 55, 120)); setPadding(0, dp(12), 0, dp(6)) }
    private fun note(value: String) = TextView(this).apply { text = value; textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, dp(2), 0, dp(10)) }
    private fun label(value: String) = TextView(this).apply { text = value; textSize = 14f; setPadding(0, dp(6), 0, dp(2)) }
    private fun edit(hint: String, value: String, numeric: Boolean = false) = EditText(this).apply { this.hint = hint; setText(value); if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    private fun human(bytes: Long): String = when { bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GiB (%d B)", bytes / (1024.0 * 1024 * 1024), bytes); bytes >= 1024L * 1024 -> String.format(Locale.US, "%.2f MiB (%d B)", bytes / (1024.0 * 1024), bytes); bytes >= 1024 -> String.format(Locale.US, "%.2f KiB (%d B)", bytes / 1024.0, bytes); else -> "$bytes B" }
}
