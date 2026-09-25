package com.example.androidgateway

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * Opens a real app-owned TCP data tunnel over an explicitly selected non-cellular upstream Network.
 * The remote test server receives an AGP/1 CONNECT preface, then raw bidirectional bytes.
 */
class DataTunnel(
    context: Context,
    private val serverHost: String,
    private val serverPort: Int,
    private val targetHost: String,
    private val targetPort: Int
) : AutoCloseable {
    private lateinit var socket: Socket
    lateinit var input: BufferedInputStream
        private set
    lateinit var output: BufferedOutputStream
        private set
    lateinit var sessionId: String
        private set
    private var connected = false

    init {
        require(serverHost.isNotBlank()) { "Test server host is required" }
        require(serverPort in 1..65535) { "Invalid test server port" }
        require(targetPort in 0..65535) { "Invalid target port" }
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
                ?: throw IOException("ConnectivityManager unavailable")
            val network = cm.activeNetwork ?: throw IOException("No active upstream network")
            val caps = cm.getNetworkCapabilities(network) ?: throw IOException("No upstream capabilities")
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                throw IOException("Data tunnel rejected: active upstream is not Wi-Fi or Ethernet (mobile data is prohibited)")
            }
            socket = network.socketFactory.createSocket()
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.connect(InetSocketAddress(serverHost, serverPort), 10_000)
            input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            sessionId = randomId()
            val preface = "AGP/1 CONNECT $targetHost $targetPort $sessionId\n"
            output.write(preface.toByteArray(StandardCharsets.US_ASCII)); output.flush()
            GatewayController.addTunnelTx(preface.length.toLong())
            val response = readLine(input)
            GatewayController.addTunnelRx((response.length + 1).toLong())
            if (response != "AGP/1 OK $sessionId") {
                throw IOException("Test server rejected tunnel: $response")
            }
            connected = true
            GatewayController.tunnelOpened(sessionId)
        } catch (e: Exception) {
            if (::socket.isInitialized) try { socket.close() } catch (_: Exception) { }
            GatewayController.setTunnel(LinkState.DEGRADED, "Data tunnel connection failed: ${e.message}")
            throw e
        }
    }

    override fun close() {
        try { socket.close() } catch (_: Exception) { }
        if (connected) {
            connected = false
            GatewayController.tunnelClosed(sessionId)
        }
    }

    fun shutdownOutput() {
        try { output.flush(); socket.shutdownOutput() } catch (_: Exception) { }
    }

    private fun randomId(): String {
        val data = ByteArray(12); SecureRandom().nextBytes(data)
        return data.joinToString("") { "%02x".format(it) }
    }

    private fun readLine(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>(128)
        repeat(1024) {
            val value = input.read()
            if (value < 0) throw IOException("Tunnel closed before handshake")
            if (value == '\n'.code) return String(bytes.toByteArray(), StandardCharsets.US_ASCII).trimEnd('\r')
            bytes.add(value.toByte())
        }
        throw IOException("Tunnel handshake line too long")
    }
}
