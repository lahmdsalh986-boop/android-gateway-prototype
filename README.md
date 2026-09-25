# Android Gateway Prototype

This repository contains a **native Android engineering prototype** for a bounded, testable question: can an Android application receive client traffic that is explicitly delivered to its proxy listener over Wi-Fi, transport it in an app-owned data tunnel to a server, and return the response to the client without using mobile data? The source includes a gateway app, a proxy-aware Android client test app, and a restricted Python test server.

It does **not** claim that a normal third-party Android app can transparently intercept all traffic from Android tethering or a Soft AP. The implementation intentionally makes the client-to-app hand-off explicit with HTTP CONNECT. That distinguishes a verified, non-root application-level relay experiment from unverified routing claims.

## Modules

| Module | Role |
| --- | --- |
| `gateway` | Gateway Android application. It runs a foreground HTTP CONNECT listener, opens an app-owned `AGP/1` TCP data tunnel over Wi-Fi/Ethernet only, manages a LocalOnlyHotspot request, provides a user-consented narrow `VpnService`/TUN diagnostic route, checks STA+AP capability, and displays real byte counters and events. |
| `client-test` | Android test client. It sends and receives deterministic 10 MiB payloads through the explicit proxy and displays endpoint SHA-256 comparisons. |
| `server` | Restricted Python laboratory harness. It supports independent data and control listeners and a loopback-only payload service. |
| `docs` | Hardware procedure and the final capability report. |

## Build

The project targets Android API 35 and has a minimum SDK of 29. With Android SDK platform 35 and Gradle 8.10.2 installed:

```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
/path/to/gradle/bin/gradle :gateway:assembleDebug :client-test:assembleDebug
```

The two APKs are emitted under each module’s `build/outputs/apk/debug/` directory. For the exact physical-device procedure, open [`docs/TEST_PROCEDURE.md`](docs/TEST_PROCEDURE.md).

The Gateway screen includes **Start TUN diagnostic**. Android displays the normal VPN consent dialog. The diagnostic installs only `10.99.0.0/24`, counts packets actually delivered to the TUN, and reports `NOT VERIFIED` for tethered-client capture unless a physical-device test demonstrates otherwise. It is not presented as a transparent hotspot-client interceptor.

## Security boundary

The Python harness permits only `127.0.0.1`, `localhost`, or `::1` as its payload target. It is intended for a controlled LAN. Do not expose it to the Internet. The Android proxy is intentionally limited to HTTP CONNECT to make the test protocol precise; it is not a production Internet proxy.
