# YouTube Skip Overlay

An Android overlay that appears while YouTube is in the foreground and provides floating skip controls.

The app uses Android Accessibility to detect YouTube and dispatch the same double-tap gestures YouTube already supports:

- `+10` taps the right side of the video player.
- `-10` taps the left side of the video player.
- Double-click volume up attempts to skip forward while YouTube is focused.
- Double-click volume down attempts to skip back while YouTube is focused.

The actual seek amount follows YouTube's own **Double-tap to seek** setting. The labels assume YouTube's default 10 second seek interval.

## Status

This is a small experimental utility tested on a Pixel 8. It currently supports portrait playback and fullscreen landscape playback.

Volume-button double-click support is experimental because Android may reserve volume keys for system volume handling on some devices or states.

## Permissions

The app requires two Android permissions that must be enabled by the user:

- **Display over other apps**: draws the floating skip controls above YouTube.
- **Accessibility service**: detects when YouTube is active and performs the double-tap gestures.

The Accessibility service is limited to foreground-window detection and gesture dispatch. It does not collect, store, or transmit video, account, or browsing data.

## Build

Requirements:

- JDK 17
- Android SDK with platform 35 installed

Create `local.properties` with your Android SDK path:

```properties
sdk.dir=/path/to/android/sdk
```

Then build the debug APK:

```sh
./gradlew :app:assembleDebug
```

Install on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup On Device

1. Open **YouTube Skip Overlay**.
2. Enable **Display over other apps**.
3. Enable the **YouTube Skip Overlay** Accessibility service.
4. Open YouTube and play a video.
5. Use the floating `-10` and `+10` buttons.

If Android shows a separate floating Accessibility shortcut button, disable that shortcut target in Android Accessibility settings. The service can remain enabled without the shortcut.

## Notes

- This app is not affiliated with YouTube or Google.
- YouTube UI changes may require adjusting the gesture target coordinates.
- The debug build is not release-signed.