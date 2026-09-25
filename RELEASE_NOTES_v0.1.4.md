# BRIXAR Gateway v0.1.4

This release implements the ordinary-Android capability category inside the main BRIXAR Gateway app.

- Device Capabilities page: Android/device, Root, System privileges, VPN, Hotspot, Tethering, transparent Hotspot-client routing.
- Real `/proc/net/arp` client observation when available; no fabricated client list.
- Explicit separation of transparent routing from Explicit Proxy.
- Clear in-app text: transparent Hotspot-client routing is unavailable to an ordinary Android app on this device; current available path is Explicit Proxy.
- Real Gateway self-test, Data Tunnel, Control Channel keepalive latency, TUN diagnostic, byte counters, event log, and data-moving pulse remain included.

The APK does not claim transparent routing success without physical evidence. It is a debug engineering build.
