package com.example.androidgateway

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Executes a real 1 MiB Gateway→Server→Gateway payload through AGP/1. */
object GatewaySelfTest {
    fun run(context: Context, host: String, dataPort: Int, onResult: (String) -> Unit) {
        thread(name = "gateway-self-test") {
            try {
                val size = 1024 * 1024
                val tunnel = DataTunnel(context, host, dataPort, "127.0.0.1", 10080)
                tunnel.use {
                    val out = it.output
                    val input = it.input
                    val downloadRequest = "DOWNLOAD $size\n".toByteArray(Charsets.US_ASCII)
                    out.write(downloadRequest); out.flush(); GatewayController.addTunnelTx(downloadRequest.size.toLong())
                    val header = readLine(input); GatewayController.addTunnelRx((header.length + 1).toLong())
                    val fields = header.split(" ")
                    if (fields.size != 3 || fields[0] != "DOWNLOAD") throw IOException("Server download response invalid")
                    val expected = fields[2]
                    val digest = MessageDigest.getInstance("SHA-256")
                    var remaining = size
                    val buffer = ByteArray(64 * 1024)
                    while (remaining > 0) {
                        val count = input.read(buffer, 0, minOf(remaining, buffer.size))
                        if (count < 0) throw IOException("Server download truncated")
                        digest.update(buffer, 0, count); remaining -= count
                        GatewayController.addTunnelRx(count.toLong())
                    }
                    val actual = hex(digest.digest())
                    if (actual != expected) throw IOException("SHA-256 mismatch: expected $expected actual $actual")
                    GatewayController.event("Gateway self-test PASS: 1 MiB server payload SHA-256 matched")
                    onResult("PASS: Gateway→Data Tunnel→Server returned 1 MiB with SHA-256 match")
                }
            } catch (e: Exception) {
                GatewayController.event("Gateway self-test FAILED: ${e.message}")
                onResult("FAIL: ${e.message}")
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String {
        val data = ArrayList<Byte>()
        while (data.size < 1024) {
            val value = input.read()
            if (value < 0) throw IOException("Server closed during self-test")
            if (value == '\n'.code) return String(data.toByteArray(), Charsets.US_ASCII).trimEnd('\r')
            data += value.toByte()
        }
        throw IOException("Server response line too long")
    }

    private fun hex(data: ByteArray) = data.joinToString("") { "%02x".format(it) }
}
