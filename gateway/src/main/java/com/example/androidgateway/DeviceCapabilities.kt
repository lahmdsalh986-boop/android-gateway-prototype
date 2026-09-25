package com.example.androidgateway

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Reports only facts observable by an ordinary APK. It deliberately does not
 * infer transparent tethering success from a hotspot or VPN icon.
 */
data class DeviceCapabilities(
    val android: String,
    val root: String,
    val systemPrivileges: String,
    val vpn: String,
    val hotspot: String,
    val tethering: String,
    val transparentClientRouting: String,
    val reasons: List<String>,
    val arpClients: List<String>
)

object DeviceCapabilityProbe {
    fun read(context: Context): DeviceCapabilities {
        val appInfo = context.applicationInfo
        val isSystem = (appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        val root = if (hasRoot()) "متاح" else "غير متاح"
        val vpn = if (VpnService.prepare(context) == null) "متاح (مصرّح حاليًا)" else "متاح بعد موافقة Android"
        val wifi = context.getSystemService(WifiManager::class.java)
        val hotspot = when {
            Build.VERSION.SDK_INT < 26 -> "غير متاح: LocalOnlyHotspot يحتاج API 26+"
            else -> "متاح للفحص: LocalOnlyHotspot فقط؛ لا يشارك Internet تلقائيًا"
        }
        val tethering = if (isSystem) "متاح حسب سياسة الجهاز" else "غير متاح لتطبيق عادي: يحتاج TETHER_PRIVILEGED/WRITE_SETTINGS وقد يفشل بالـcarrier entitlement"
        val reasons = mutableListOf<String>()
        reasons += "Hotspot: LocalOnlyHotspot شبكة محلية فقط ولا يضمن Internet forwarding."
        reasons += "Tethering: لا توجد Public API عادية لتسليم حزم العملاء إلى التطبيق."
        reasons += "VPN: TUN يستقبل فقط ما يوجهه Android إلى VPN؛ لا يثبت مرور Hotspot clients."
        reasons += "Transparent routing: غير متاح لتطبيق Android عادي على هذا الجهاز؛ يتطلب System/Root أو Gateway خارجي."
        if (root == "غير متاح") reasons += "Root: لم يثبت وجود su قابل للتنفيذ."
        if (!isSystem) reasons += "System privileges: التطبيق ليس System/Privileged app."
        return DeviceCapabilities(
            android = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            root = root,
            systemPrivileges = if (isSystem) "متاحة" else "غير متاحة",
            vpn = vpn,
            hotspot = hotspot,
            tethering = tethering,
            transparentClientRouting = "غير متاح",
            reasons = reasons,
            arpClients = arpClients()
        )
    }

    private fun hasRoot(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v su && su -c id"))
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val finished = if (Build.VERSION.SDK_INT >= 26) process.waitFor(1200, TimeUnit.MILLISECONDS) else { process.waitFor(); true }
        finished && process.exitValue() == 0 && output.contains("uid=0")
    } catch (_: Exception) { false }

    private fun arpClients(): List<String> = try {
        File("/proc/net/arp").useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 6 && parts[3] != "00:00:00:00:00:00") "${parts[0]}  ${parts[3]}  ${parts[5]}" else null
            }.distinct().toList()
        }
    } catch (_: Exception) { emptyList() }
}
