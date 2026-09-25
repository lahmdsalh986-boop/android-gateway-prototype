package com.example.gatewaytestclient

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

/** Real proxy-aware client for the laboratory server. It carries two 10 MiB byte streams through CONNECT. */
class MainActivity : Activity() {
    private lateinit var gatewayHost: EditText
    private lateinit var gatewayPort: EditText
    private lateinit var payloadPort: EditText
    private lateinit var output: TextView
    private lateinit var run: Button

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(buildUi()) }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(18), dp(16), dp(18)) }
        root.addView(TextView(this).apply { text = "Gateway Test Client"; textSize = 24f; setTextColor(Color.rgb(10, 55, 120)) })
        root.addView(TextView(this).apply { text = "Runs real 10 MiB server→client and client→server SHA-256 checks through the Gateway HTTP CONNECT proxy."; textSize = 13f; setPadding(0, dp(8), 0, dp(10)) })
        gatewayHost = field("Gateway phone IPv4", "")
        gatewayPort = field("Gateway proxy port", "8080", true)
        payloadPort = field("Test payload port on server", "10080", true)
        root.addView(gatewayHost); root.addView(gatewayPort); root.addView(payloadPort)
        run = Button(this).apply { text = "Run 10 MiB bidirectional test"; setOnClickListener { runTest() } }
        root.addView(run)
        output = TextView(this).apply { text = "Awaiting test."; textSize = 13f; typeface = android.graphics.Typeface.MONOSPACE; setPadding(0, dp(12), 0, 0) }
        root.addView(output)
        return ScrollView(this).apply { addView(root) }
    }

    private fun runTest() {
        val host = gatewayHost.text.toString().trim(); val port = gatewayPort.text.toString().toIntOrNull(); val target = payloadPort.text.toString().toIntOrNull()
        if (host.isBlank() || port == null || target == null || port !in 1..65535 || target !in 1..65535) { toast("Enter valid proxy settings"); return }
        run.isEnabled = false; output.text = "Connecting to explicit proxy…"
        thread(name = "gateway-test") {
            val result = try {
                val down = receiveFromServer(host, port, target, 10 * 1024 * 1024)
                val up = sendToServer(host, port, target, 10 * 1024 * 1024)
                "SERVER → CLIENT\\nExpected SHA-256: ${down.first}\\nClient SHA-256:   ${down.second}\\nResult: ${if (down.first == down.second) "PASS" else "FAIL"}\\n\\nCLIENT → SERVER\\nClient SHA-256:   ${up.first}\\nServer SHA-256:   ${up.second}\\nResult: ${if (up.first == up.second) "PASS" else "FAIL"}\\n\\nOVERALL: ${if (down.first == down.second && up.first == up.second) "PASS" else "FAIL"}"
            } catch (e: Exception) { "NOT VERIFIED — ${e.message}" }
            runOnUiThread { output.text = result; run.isEnabled = true }
        }
    }

    private fun receiveFromServer(proxyHost: String, proxyPort: Int, targetPort: Int, bytes: Int): Pair<String, String> = tunnel(proxyHost, proxyPort, targetPort).use { io ->
        writeLine(io.out, "DOWNLOAD $bytes")
        val reply = readLine(io.input).split(" ")
        if (reply.size != 3 || reply[0] != "DOWNLOAD" || reply[1].toInt() != bytes) throw IOException("Unexpected payload response")
        val expected = reply[2]
        val digest = MessageDigest.getInstance("SHA-256"); copyExact(io.input, bytes, digest, null); expected to hex(digest.digest())
    }

    private fun sendToServer(proxyHost: String, proxyPort: Int, targetPort: Int, bytes: Int): Pair<String, String> = tunnel(proxyHost, proxyPort, targetPort).use { io ->
        writeLine(io.out, "UPLOAD $bytes")
        val digest = MessageDigest.getInstance("SHA-256"); generateDeterministic(bytes, io.out, digest); io.out.flush()
        val local = hex(digest.digest()); val reply = readLine(io.input).split(" ")
        if (reply.size != 2 || reply[0] != "UPLOAD-OK") throw IOException("Unexpected upload acknowledgement")
        local to reply[1]
    }

    private fun tunnel(proxyHost: String, proxyPort: Int, targetPort: Int): Io {
        val socket = Socket(proxyHost, proxyPort); socket.tcpNoDelay = true
        val input = BufferedInputStream(socket.getInputStream(), 64 * 1024); val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
        out.write("CONNECT 127.0.0.1:$targetPort HTTP/1.1\r\nHost: 127.0.0.1:$targetPort\r\n\r\n".toByteArray(Charsets.US_ASCII)); out.flush()
        val header = readHttpHeader(input); if (!header.startsWith("HTTP/1.1 200")) { socket.close(); throw IOException("Proxy rejected CONNECT: ${header.substringBefore("\r\n")}") }
        return Io(socket, input, out)
    }

    private data class Io(val socket: Socket, val input: BufferedInputStream, val out: BufferedOutputStream) : AutoCloseable { override fun close() { try { socket.close() } catch (_: Exception) {} } }
    private fun writeLine(out: BufferedOutputStream, line: String) { out.write((line + "\\n").toByteArray(Charsets.US_ASCII)); out.flush() }
    private fun readLine(input: BufferedInputStream): String { val b = ArrayList<Byte>(); while (b.size < 512) { val x = input.read(); if (x < 0) throw IOException("Unexpected EOF"); if (x == '\n'.code) return String(b.toByteArray(), Charsets.US_ASCII).trimEnd('\r'); b += x.toByte() }; throw IOException("Line too long") }
    private fun readHttpHeader(input: BufferedInputStream): String { val b = ArrayList<Byte>(); var m=0; while (b.size < 16384) { val x=input.read(); if(x<0) throw IOException("EOF before proxy response"); b+=x.toByte(); m=if(m==0&&x=='\r'.code)1 else if(m==1&&x=='\n'.code)2 else if(m==2&&x=='\r'.code)3 else if(m==3&&x=='\n'.code)4 else if(x=='\r'.code)1 else 0; if(m==4)return String(b.toByteArray(), Charsets.ISO_8859_1) }; throw IOException("Header too long") }
    private fun copyExact(input: BufferedInputStream, size: Int, digest: MessageDigest, out: BufferedOutputStream?) { var left=size; val b=ByteArray(64*1024); while(left>0){ val read=input.read(b,0,minOf(left,b.size)); if(read<0)throw IOException("Payload truncated"); digest.update(b,0,read); out?.write(b,0,read); left-=read } }
    private fun generateDeterministic(size: Int, out: BufferedOutputStream, digest: MessageDigest) { var left=size; var counter=0L; while(left>0){ val block=MessageDigest.getInstance("SHA-256").digest("AGP-PAYLOAD-$counter".toByteArray()); val n=minOf(left,block.size); out.write(block,0,n); digest.update(block,0,n); left-=n; counter++ } }
    private fun hex(data: ByteArray)=data.joinToString(""){"%02x".format(it)}
    private fun field(hint:String, value:String, numeric:Boolean=false)=EditText(this).apply{this.hint=hint;setText(value);if(numeric)inputType=android.text.InputType.TYPE_CLASS_NUMBER}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt(); private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_LONG).show()
}
