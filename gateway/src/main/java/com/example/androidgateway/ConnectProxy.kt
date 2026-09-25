package com.example.androidgateway

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** A deliberately narrow explicit proxy: it accepts only HTTP CONNECT. */
class ConnectProxy(
    private val context: Context,
    private val bindPort: Int,
    private val serverHost: String,
    private val serverPort: Int
) : Closeable {
    private val running = AtomicBoolean(false)
    private val worker: ExecutorService = Executors.newCachedThreadPool()
    private var listener: ServerSocket? = null

    fun start() {
        check(running.compareAndSet(false, true)) { "Proxy already started" }
        listener = ServerSocket(bindPort).apply { reuseAddress = true }
        GatewayController.setGateway(LinkState.READY, "HTTP CONNECT proxy listening on 0.0.0.0:$bindPort")
        worker.execute {
            while (running.get()) {
                try {
                    val client = listener?.accept() ?: break
                    client.tcpNoDelay = true
                    worker.execute { handleClient(client) }
                } catch (e: IOException) {
                    if (running.get()) GatewayController.setError("Proxy accept failed: ${e.message}")
                }
            }
        }
    }

    override fun close() {
        running.set(false)
        try { listener?.close() } catch (_: Exception) { }
        worker.shutdownNow()
    }

    private fun handleClient(clientSocket: Socket) {
        val peer = clientSocket.inetAddress?.hostAddress ?: "unknown"
        GatewayController.clientOpened(peer)
        try {
            clientSocket.use { client ->
                val clientIn = BufferedInputStream(client.getInputStream(), 64 * 1024)
                val clientOut = BufferedOutputStream(client.getOutputStream(), 64 * 1024)
                val request = readHeader(clientIn)
                GatewayController.addWifiRx(request.toByteArray(StandardCharsets.ISO_8859_1).size.toLong())
                val parsed = parseConnect(request)
                val tunnel = DataTunnel(context, serverHost, serverPort, parsed.first, parsed.second)
                tunnel.use {
                    val response = "HTTP/1.1 200 Connection Established\\r\\nProxy-Agent: AndroidGatewayPrototype/0.1\\r\\n\\r\\n"
                    clientOut.write(response.toByteArray(StandardCharsets.US_ASCII)); clientOut.flush()
                    GatewayController.addWifiTx(response.length.toLong())
                    relay(clientIn, clientOut, tunnel)
                }
            }
        } catch (e: Exception) {
            GatewayController.event("Client $peer failed: ${e.message}")
            try {
                clientSocket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\\r\\nConnection: close\\r\\n\\r\\n".toByteArray(StandardCharsets.US_ASCII))
            } catch (_: Exception) { }
        } finally {
            GatewayController.clientClosed(peer)
        }
    }

    private fun relay(clientIn: BufferedInputStream, clientOut: BufferedOutputStream, tunnel: DataTunnel) {
        val upstream = Thread {
            try {
                copyCounted(clientIn, tunnel.output, GatewayController::addWifiRx, GatewayController::addTunnelTx)
                tunnel.shutdownOutput()
            } catch (e: IOException) {
                GatewayController.setTunnel(LinkState.DEGRADED, "Client-to-tunnel flow interrupted: ${e.message}")
            }
        }.apply { name = "gateway-client-to-tunnel"; isDaemon = true }
        val downstream = Thread {
            try {
                copyCounted(tunnel.input, clientOut, GatewayController::addTunnelRx, GatewayController::addWifiTx)
                try { clientOut.flush() } catch (_: Exception) { }
            } catch (e: IOException) {
                GatewayController.setTunnel(LinkState.DEGRADED, "Tunnel-to-client flow interrupted: ${e.message}")
            }
        }.apply { name = "gateway-tunnel-to-client"; isDaemon = true }
        upstream.start(); downstream.start()
        upstream.join(); downstream.join()
    }

    private fun copyCounted(
        input: BufferedInputStream,
        output: BufferedOutputStream,
        inputCounter: (Long) -> Unit,
        outputCounter: (Long) -> Unit
    ) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return
            output.write(buffer, 0, read); output.flush()
            inputCounter(read.toLong()); outputCounter(read.toLong())
        }
    }

    private fun readHeader(input: BufferedInputStream): String {
        val out = ByteArrayOutputStream()
        var match = 0
        while (out.size() < 16 * 1024) {
            val b = input.read(); if (b < 0) throw IOException("Client closed before proxy request")
            out.write(b)
            match = when {
                match == 0 && b == '\r'.code -> 1
                match == 1 && b == '\n'.code -> 2
                match == 2 && b == '\r'.code -> 3
                match == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (match == 4) return String(out.toByteArray(), StandardCharsets.ISO_8859_1)
        }
        throw IOException("Proxy request header exceeds 16 KiB")
    }

    private fun parseConnect(header: String): Pair<String, Int> {
        val first = header.substringBefore("\\r\\n").trim().split(Regex("\\s+"))
        if (first.size != 3 || !first[0].equals("CONNECT", true)) {
            throw IOException("Only HTTP CONNECT is supported by this prototype")
        }
        val authority = first[1]
        val cut = authority.lastIndexOf(':')
        if (cut <= 0 || cut == authority.length - 1) throw IOException("CONNECT must use host:port")
        val host = authority.substring(0, cut)
        val port = authority.substring(cut + 1).toIntOrNull() ?: throw IOException("Invalid CONNECT port")
        if (port !in 1..65535) throw IOException("CONNECT port outside range")
        return host to port
    }
}
