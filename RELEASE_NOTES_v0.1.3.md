# Android Gateway Prototype v0.1.3

Expanded implementation aligned with the engineering brief.

- Real Gateway→Server 1 MiB self-test from inside the Gateway app.
- Real SHA-256 comparison and failure detail in the event log/toast.
- Separate control-channel PING/ACK latency measurement.
- Event-driven `DATA MOVING` indicator based on real byte-counter changes, not a timer.
- Visible path monitor and real Wi-Fi/client/tunnel/TUN counters.
- Real user-consented VpnService/TUN diagnostic remains available.
- LocalOnlyHotspot behavior and Android limitations remain explicitly reported.

The explicit proxy path is the supported ordinary-app proof. Transparent interception of arbitrary tethered-client traffic remains device/system dependent and is not claimed without physical-device evidence. APKs are debug prototype builds.
