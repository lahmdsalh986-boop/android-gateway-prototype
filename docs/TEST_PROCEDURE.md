# Android Gateway Prototype — Hardware Test Procedure

## Purpose and strict success condition

This procedure verifies an **explicit-proxy** data path, not transparent packet interception. The success condition is that a client Android phone opens an HTTP CONNECT session to the gateway Android app over the local Wi-Fi network, the gateway opens its own independent TCP data-tunnel session to the test server, and the payload returns through the same tunnel to the client. Both directions must transfer 10 MiB and report identical SHA-256 values.

> A completed build or a running server is not a pass. The pass requires the two real Android devices, a reachable non-cellular upstream network for the gateway, and matching on-device SHA-256 values.

## Equipment and topology

Use three endpoints: a **gateway Android phone** with the `gateway` APK, a **client Android phone** with the `client-test` APK, and a computer or reachable host running the supplied Python harness. The test server must be reachable from the gateway using Wi-Fi or Ethernet. Do not enable mobile data as the upstream. The client may join the same local Wi-Fi LAN as the gateway, or may join a `LocalOnlyHotspot` started by the gateway if the device supports concurrent hotspot and Wi-Fi upstream operation.

```text
Client Test App ── explicit HTTP CONNECT ──> Gateway Android App
                                                     │
                                                     │ AGP/1 TCP Data Tunnel
                                                     ▼
                                              Test Server:9000
                                              Control:9001 (optional)
                                              Payload:10080
```

The server deliberately accepts only the loopback payload target. It cannot act as an open proxy.

## Start the test server

Run the following command on the server host and record its Wi-Fi-reachable IP address. The default ports are TCP 9000 for data, 9001 for control, and 10080 for the payload service.

```bash
cd server
./run-test-server.sh
```

The expected startup log states that all three listeners are bound to `0.0.0.0`. Permit those TCP ports through the server host firewall only within the controlled test network.

## Install the two Android packages

Build or install the two APKs produced by the Gradle project. Install `gateway-debug.apk` on the gateway phone and `client-test-debug.apk` on the client phone. Open the gateway application and grant the requested nearby-Wi-Fi/location permission if you will use LocalOnlyHotspot. Notifications are optional for the data-plane test, but enabling them makes foreground-service operation visible.

## Configure and start the gateway

On the gateway phone, ensure the active upstream is **Wi-Fi or Ethernet**, not cellular. Enter the test-server IP in **Test server endpoint**, retain data port `9000`, and retain proxy port `8080`. Tap **Start gateway**. The screen must show `Gateway: READY` and a listener at `0.0.0.0:8080`. Record the gateway phone IPv4 address shown on the screen.

If testing LocalOnlyHotspot, tap **Start local hotspot** and join the displayed SSID with the client phone. This action provides the Wi-Fi link only. It does not make the application a transparent router and it does not guarantee that a device supports concurrent hotspot plus Wi-Fi upstream. If the hotspot cannot coexist with the upstream, use a common Wi-Fi LAN for the initial explicit-proxy proof and record the device limitation.

For the separate VPN diagnostic, tap **Start TUN diagnostic** and accept Android's VPN consent dialog. The app installs only the narrow `10.99.0.0/24` test route and increments `TUN RX` only when Android delivers a packet to the TUN. This check must be recorded independently: a connected VPN icon or a non-zero TUN counter is not proof that tethered client packets enter the TUN. The intended result for an ordinary Android build may be **NOT VERIFIED** or **BLOCKED BY ANDROID** for hotspot-client traffic.

The Gateway screen also contains **Run real Gateway → Server 1 MiB test**. Start the gateway first, then press this button. It opens a new real data-tunnel session, requests 1 MiB from the server payload service, reads the response, and compares SHA-256. A toast and event-log entry are produced from the actual result. The path monitor shows `DATA MOVING` only after a real counter changes. A control-channel latency value appears only after a real `PING/ACK` exchange.

## Execute the real bidirectional test

On the client phone, enter the gateway IPv4 address, proxy port `8080`, and payload port `10080`. Tap **Run 10 MiB bidirectional test**. The client first requests a server-generated deterministic 10 MiB download, hashes it, then generates a deterministic 10 MiB upload and compares the test-server acknowledgment hash.

A valid pass has all of the following observable evidence:

| Evidence | Required value |
| --- | --- |
| Client screen, server-to-client | `Result: PASS` with matching server/client SHA-256 |
| Client screen, client-to-server | `Result: PASS` with matching client/server SHA-256 |
| Client screen | `OVERALL: PASS` |
| Gateway screen | Client state `CONNECTED` during the test and non-zero Wi-Fi RX/TX and Data Tunnel RX/TX counters |
| Test server log | A data tunnel session plus one `server→client payload` and one `client→server payload` entry |
| Control test, if started | Control state `CONNECTED` and small PING/ACK events; no payload bytes are carried there |

Record the exact hashes, byte counters, gateway OS version, handset model, upstream type, hotspot result, and server log excerpt in the final report. Do not replace missing evidence with an inference.

## Failure and recovery checks

Run these checks after a basic pass. Start the gateway without the test server to confirm that a client session returns a controlled 502 rather than a crash. Start the gateway without a client and confirm it remains `READY`. During a transfer, stop the Python test server and confirm the gateway reports a failed or degraded data tunnel without crashing. Restart the test server and run the client test again. Disconnect and reconnect the client and verify that a new session is counted. If any check fails, record it as **NOT VERIFIED** or **FAILED**, including the error shown by the client, gateway event log, and server log.

## Boundary of this prototype

This is a real application-level relay test. It proves traffic that is **explicitly directed to the app’s proxy listener**. It does not prove that a normal Android app can invisibly obtain every packet from clients of Android tethering or a Soft AP. That separate question depends on Android routing, vendor support, system privileges, and the API analysis in the technical report.
