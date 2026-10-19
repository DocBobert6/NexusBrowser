# Nexus Browser

**Android TV web browser** designed to be competitive on video playback and ad blocking, with excellent remote control support and a Material 3 Expressive look.

Optimized for the **2024 Onn 4K Pro** (Amlogic S905X4, 3 GB RAM, 32-bit userspace / `armeabi-v7a`) and other Google TV / Android TV devices.

## Key Goals

- Competitive **native video player** (ExoPlayer handoff from WebView)
- Strong **ad blocking** (host-based starter + easy expansion to EasyList/AdGuard)
- Perfect **D-pad / remote** experience (Compose for TV)
- **Material 3 Expressive**-inspired light + dark themes
- Fully functional Android TV app (Leanback launcher, no touchscreen required)
- ARMv7 (`armeabi-v7a`) primary + `arm64-v8a` for future devices

## Current Status (0.2.0-alpha)

- Compose for TV + Material 3 theming with **working light/dark themes** (persisted, dynamic color optional)
- **D-pad mouse cursor** — smooth Mac-style vector cursor, click synthesis
- Home screen with search + quick links
- WebView browser with **real ad blocking** (EasyList + AdGuard Base, bundled snapshot + weekly refresh)
- **Video handoff**: direct media links, detected `<video>` elements, and fullscreen video all route to the native ExoPlayer screen
- Settings screen (adblock + theme + cursor toggles)
- Proper Android TV manifest

## Build & Run

### Requirements
- Android Studio (latest stable or Canary with Gemini / AI agent recommended)
- JDK 17
- Android SDK 35

### Steps
1. Open the project in Android Studio.
2. Let Gradle sync.
3. Connect your Onn 4K Pro (or use an Android TV emulator).
4. Run the `app` configuration.

### Sideload to Onn 4K Pro
```bash
# Enable Developer options + USB debugging / Network debugging on the box
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### ABI Note
The build includes both `armeabi-v7a` and `arm64-v8a`. The Onn 4K Pro (and most current Google TV boxes) will receive the 32-bit variant.

## Next Steps (recommended order)

1. Expand the ad blocker with real filter lists (EasyList, AdGuard Base, etc.).
2. Improve video detection + seamless handoff (JS bridge or resource inspection → ExoPlayer with position continuity).
3. Add MediaSession for proper system media key handling.
4. Bookmarks / History with Room or DataStore.
5. Better focus management and remote polish.
6. User-agent switcher and desktop mode.
7. Iterate with Android Studio AI agent using the prompt in `AI_STUDIO_PROMPT.txt`.

## Architecture

```
com.nexus.browser
├── MainActivity
├── ui/
│   ├── theme/          # Material 3 Expressive-inspired colors & type
│   ├── screens/        # Home, Browser, Player, Settings
│   └── components/
├── adblock/            # Host-based blocker (expandable)
├── browser/            # Future WebView helpers
├── player/             # Future ExoPlayer + MediaSession helpers
└── data/               # Future Room / DataStore
```

## License

You own this project. Do whatever you want with it.
