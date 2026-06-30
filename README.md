# youtube-smartwatch-preview

Phone-assisted preview pipeline for Wear OS smartwatches.

V0 proves the battery-critical transport path before touching YouTube:

- the phone app runs a foreground TCP streaming service on port `45990`
- the phone hardware-encodes a synthetic `360x360`, `12fps` H.264 stream
- the watch app reconnects over Wi-Fi, hardware-decodes the stream, and renders fullscreen
- audio, YouTube auth, OAuth secrets, and Google API keys are intentionally out of scope for V0

## Tooling

This repo is intentionally storage-light. It uses JDK 17, the Android command-line SDK, and the Gradle wrapper. Android Studio is not required for V0.

Installed SDK packages used by the current build:

- `platform-tools`
- `platforms;android-37.0`
- `build-tools;36.0.0`

## Build

```powershell
$jdk = Get-ChildItem -Path "$env:ProgramFiles\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Sort-Object Name -Descending | Select-Object -First 1
$sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:PATH = "$($jdk.FullName)\bin;$sdkRoot\cmdline-tools\latest\bin;$sdkRoot\platform-tools;$env:PATH"

.\gradlew.bat test
.\gradlew.bat :phone:assembleDebug :wear:assembleDebug
```

Debug APKs are written to:

- `phone/build/outputs/apk/debug/phone-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

## Run V0

Install the phone app on the Android phone:

```powershell
adb install -r .\phone\build\outputs\apk\debug\phone-debug.apk
```

Install the watch app on the Wear OS watch:

```powershell
adb install -r .\wear\build\outputs\apk\debug\wear-debug.apk
```

Start the phone app and tap `Start stream`. Then launch the watch preview with the phone's Wi-Fi IP address:

```powershell
adb shell am start -n cl.dily.youtubepreview.wear/.MainActivity --es host <PHONE_WIFI_IP>
```

The watch UI stores the last provided host and reconnects with exponential backoff. The preview intentionally uses center-fill rendering so rectangular video edges can be lost on a circular display.

## Protocol

The phone sends a newline-terminated JSON handshake:

```json
{"protocol":"yswp-h264","version":1,"codec":"video/avc","width":360,"height":360,"fps":12}
```

Each H.264 access unit is framed as:

```text
uint32 length
uint64 ptsUs
uint32 flags
payload bytes
```

`flags` are the Android `MediaCodec` buffer flags, including codec-config frames.
