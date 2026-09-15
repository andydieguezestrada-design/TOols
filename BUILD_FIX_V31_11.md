# TOols V31.11 — Lint Fix

## Fix applied
Added optional ChromeOS hardware declarations for permissions that do not make the hardware mandatory:

- `android.hardware.camera` — `required="false"`
- `android.hardware.microphone` — `required="false"`

This resolves the reported Android Lint error `PermissionImpliesUnsupportedChromeOsHardware` for `android.permission.CAMERA` while preserving camera/microphone permissions for devices that support them.

## Version
- versionName: 3.1.11
- versionCode: 42

## Validation
The project was inspected after modification. A local Gradle lint run could not be executed because the ZIP does not contain `gradle-wrapper.jar` and the environment does not provide a usable global Gradle executable. GitHub Actions remains the authoritative build/lint verification.
