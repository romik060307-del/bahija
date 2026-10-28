# RK Android WebView APK — GitHub Actions

## Build
1. Extract this ZIP. Upload the CONTENTS of `rk-android` to the root of a GitHub repository.
2. Open **Actions** → **Build RK APK** → **Run workflow** (or push to `main`).
3. Download the `RK-Android-APK` artifact from the successful workflow and extract `app-debug.apk`.

## Login and offline behavior
- WebView cookies and DOM storage are enabled, so the website may retain a login session if its authentication system supports persistent sessions.
- When the phone is offline, WebView is configured to try its cache. When online, it returns to normal network loading.
- **Important:** this is only a cache fallback, NOT a guarantee that the entire website is downloaded. Android WebView cache may omit pages, scripts, images, API responses, login state, and video files. First open the pages while online for a chance to cache resources.
- Full offline support requires the website source and an offline/PWA strategy: cache static files with a service worker, store required records locally (IndexedDB/SQLite), and queue changes until reconnection. Server-only login, live data, uploads, messaging, and other APIs cannot work fully offline without specific implementation.

## Permissions
Camera, microphone, and location prompts are bridged for supported website APIs. Contacts, Android notifications, background tasks, and other native features may require additional native implementation. Permissions are granted only after the user approves them.

## Website URL
Configured start URL: `https://keen-sprinkles-1c71ec.netlify.app/`. Use a stable HTTPS production URL; temporary tunnels may expire or redirect.


## Catatan WebRTC / panggilan
Versi ini memperkuat dukungan WebView untuk kamera/mikrofon (WebRTC), hardware acceleration, dan pemutaran media. Panggilan tetap bergantung pada implementasi situs, HTTPS, Android System WebView yang mendukung WebRTC, izin perangkat, serta layanan panggilan yang digunakan situs. Jika masih gagal, perlu log error dan kode situs panggilan untuk memperbaiki penyebab spesifiknya.
