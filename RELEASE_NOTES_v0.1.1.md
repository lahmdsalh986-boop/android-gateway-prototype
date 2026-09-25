# Android Gateway Prototype v0.1.1

This update adds the missing Android VPN/TUN diagnostic component requested in the engineering brief.

## Added

- User-consented `VpnService` with a real TUN interface.
- Narrow diagnostic route `10.99.0.0/24`.
- TUN packet byte counter and explicit VPN/TUN status.
- STA+AP capability display.
- Explicit statement that TUN visibility does not prove tethered/Soft-AP client interception.
- Updated Arabic capability report and hardware test procedure.

The primary real data path remains the explicit HTTP CONNECT client path into the app-owned TCP Data Tunnel. The APK is a debug prototype, not a production-signed build.
