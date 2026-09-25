package com.example.androidgateway

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/** Separate control socket. It sends only a handshake and PING/ACK frames, never client payload. */
class ControlChannel(private val context: Context, private val host: String, private val port: Int) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var socket: Socket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread {
            while (running.get()) {
                try {
                    GatewayController.setControl(LinkState.STARTING, "Connecting control channel")
                    val cm = context.getSystemService(android.net.ConnectivityManager::class.java) ?: throw IOException("No ConnectivityManager")
                    val network = cm.activeNetwork ?: throw IOException("No active upstream")
                    val caps = cm.getNetworkCapabilities(network) ?: throw IOException("No upstream capabilities")
                    if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        throw IOException("Control channel refuses mobile data")
                    }
                    socket = network.socketFactory.createSocket().apply { connect(InetSocketAddress(host, port), 10_000); tcpNoDelay = true }
                    val input = BufferedInputStream(socket!!.getInputStream())
                    val output = BufferedOutputStream(socket!!.getOutputStream())
                    output.write("AGP/1 CONTROL\\n".toByteArray()); output.flush()
                    if (readLine(input) != "AGP/1 CONTROL-OK") throw IOException("Control handshake rejected")
                    GatewayController.setControl(LinkState.CONNECTED, "Control channel connected (PING/ACK only)")
                    var sequence = 1
                    while (running.get()) {
                        val ping = "PING $sequence\\n"
                        val startedAt = System.nanoTime()
                        output.write(ping.toByteArray()); output.flush()
                        if (readLine(input) != "ACK $sequence") throw IOException("Unexpected control ACK")
                        GatewayController.setControlLatency((System.nanoTime() - startedAt) / 1_000_000L)
                        GatewayController.event("Control ACK $sequence")
                        sequence += 1
                        Thread.sleep(5_000)
                    }
                } catch (e: Exception) {
                    if (running.get()) {
                        GatewayController.setControl(LinkState.DEGRADED, "Control reconnect: ${e.message}")
                        try { Thread.sleep(3_000) } catch (_: InterruptedException) { }
                    }
                } finally { try { socket?.close() } catch (_: Exception) { }; socket = null }
            }
        }.apply { name = "gateway-control"; isDaemon = true; start() }
    }

    override fun close() { running.set(false); try { socket?.close() } catch (_: Exception) { }; thread?.interrupt() }

    private fun readLine(input: BufferedInputStream): String {
        val out = ArrayList<Byte>()
        repeat(256) { if (true) {
            val b = input.read(); if (b < 0) throw IOException("Control socket closed")
            if (b == '\n'.code) return String(out.toByteArray(), Charsets.US_ASCII).trimEnd('\r')
            out.add(b.toByte())
        }}
        throw IOException("Control line too long")
    }
}
