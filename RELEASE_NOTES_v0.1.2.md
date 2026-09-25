# Android Gateway Prototype v0.1.2

Compatibility rebuild to address Android package parsing/install failures on devices older than Android 10.

- Gateway and test client minimum SDK changed to Android 6.0/API 23.
- Foreground-service and LocalOnlyHotspot calls are guarded for older Android versions.
- APKs verified with `unzip -tq` and APK Signature Scheme v2.
- Gateway version: 0.1.2, versionCode 2.
- This remains a debug engineering prototype; it is not a production-signed distribution build.
