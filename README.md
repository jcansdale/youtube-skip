# YouTube Skip Overlay

An Android overlay that appears while YouTube is in the foreground and provides floating skip controls.

The app uses Android Accessibility to detect YouTube and dispatch the same double-tap gestures YouTube already supports:

- `+10` taps the right side of the video player.
- `-10` taps the left side of the video player.
- Double-click volume up attempts to skip forward while YouTube is focused.
- Double-click volume down attempts to skip back while YouTube is focused.

It also includes an optional experimental **auto skip ahead** feature. When enabled in the app settings and notification access is allowed, the app watches YouTube's media session, resolves YouTube's Smart Skip / Jump Ahead metadata when available, and automatically seeks to YouTube's target when playback enters the cue range. A short boing sound plays when an automatic skip happens.

The manual gesture seek amount follows YouTube's own **Double-tap to seek** setting. The labels assume YouTube's default 10 second seek interval.

## Status

This is a small experimental utility tested on a Pixel 8. It currently supports portrait playback and fullscreen landscape playback.

Volume-button double-click support is experimental because Android may reserve volume keys for system volume handling on some devices or states.

## Permissions

The app uses Android permissions/features that must be enabled by the user:

- **Display over other apps**: draws the floating skip controls above YouTube.
- **Accessibility service**: detects when YouTube is active and performs the double-tap gestures.
- **Notification access**: reads YouTube's active media session so auto skip ahead can prefetch metadata and seek to the Smart Skip target.

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
4. Enable **Notification access** for automatic Smart Skip / Jump Ahead.
5. Turn on **Auto skip ahead** in the app.
6. Open YouTube and play a video.
7. Use the floating `-10` and `+10` buttons, or let auto skip ahead trigger when Smart Skip metadata is available.

The app screen also includes controls for showing/hiding overlay buttons, enabling volume double-click gestures, enabling auto skip ahead, adjusting overlay opacity, and resetting the dragged overlay position.

The app also exposes launcher shortcuts for **Skip forward** and **Skip back**. These are intended for experiments with Pixel Quick Tap or launchers that can run app shortcuts.

If Android shows a separate floating Accessibility shortcut button, disable that shortcut target in Android Accessibility settings. The service can remain enabled without the shortcut.

## Notes

- This app is not affiliated with YouTube or Google.
- YouTube UI changes may require adjusting the gesture target coordinates.
- The debug build is not release-signed.