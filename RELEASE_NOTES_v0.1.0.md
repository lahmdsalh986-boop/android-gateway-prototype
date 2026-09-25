# Android Gateway Prototype v0.1.0

This is a debug prototype for a real application-layer experiment:

Client Android → explicit HTTP CONNECT proxy → Gateway Android app → app-owned TCP data tunnel → test server.

The release includes two debug APKs:

- `gateway-debug.apk`: install on the gateway phone.
- `gateway-test-client-debug.apk`: install on the client phone.

The project does not claim transparent interception of arbitrary Android tethering/Soft AP client traffic. See `docs/ANDROID_CAPABILITY_AND_VERIFICATION_REPORT_AR.md` and `docs/TEST_PROCEDURE.md`.

The APKs are debug builds intended for controlled testing, not production distribution.
