# Aegis Browser (repo: ai-browser)

An original, premium, privacy-first Android browser with an AI task panel —
powered by Kotlin + Jetpack Compose (Material 3), Android System WebView, Room,
ViewModel/StateFlow/Coroutines, and a provider-agnostic AI layer.

The app previously shipped as "AI Browser" v1.0.0. This release is the UI/UX
redesign + privacy hardening pass (v1.1.0): same applicationId
(`com.zai.aibrowser`), same signing key, so self-update installs over it cleanly.

## Status: v1.1.0 shipped

- **Identity**: original "Aegis" visual identity (teal/mint shield+spark), light
  and dark themes, consistent shapes and typography.
- **Home screen**: one prominent field (URL / search / AI task) with explicit
  Browse-vs-AI mode chips, real shortcuts (bookmarks first), a real
  "trackers blocked" counter and a quick Erase action.
- **Browser controls**: one-handed bottom chrome — back, forward, address
  (readable domain), tabs, menu — with a progress line, a floating AI chip and
  48dp+ touch targets. Tapping the lock opens the site privacy dashboard.
- **Privacy dashboard** (site sheet): verified facts only — domain, HTTPS state
  (with an "encryption ≠ trust" note), real per-page blocked count, per-site
  tracker exception toggle, report-broken-site link, engine-level permission
  policy, and site-data clearing with its true (global) scope.
- **Tracking protection**: request-based blocking of third-party subresources
  against the StevenBlack/hosts list (MIT, 71k+ domains, embedded asset,
  refreshed via `scripts/gen_tracker_list.py`). Proper domain matching (parent
  walk, first-party exception via approximated eTLD+1), main frames never
  blocked, per-site exceptions, real floor counts. See limitations below.
- **AI tasks**: development preview with the on-device mock provider. The panel
  never fakes agent progress: states stay PENDING, actions are labeled
  SUGGESTED vs EXECUTED, Stop really cancels, and the off-device disclosure is
  explicit. No page content is collected; nothing leaves the device.
- **Self-update** (from v1.0.0, unchanged): GitHub Releases check → download →
  platform installer. Publishing a new tag `vX.Y.Z` is enough to ship updates.

## Build & test

```bash
./gradlew testDebugUnitTest   # unit tests (TrackerBlocker matching, UrlNormalizer)
./gradlew assembleRelease     # signed release APK (R8 minified)
```

Latest verified results (this tree): 23/23 unit tests pass;
`assembleRelease` BUILD SUCCESSFUL; APK signed with the release keystore,
versionCode 2, versionName 1.1.0.

## Privacy & security notes

- No JavaScript bridge to page content; page content is treated as untrusted data.
- SSL errors are never bypassed; mixed content is never allowed.
- Third-party cookies are blocked by default (toggle in Settings, with breakage note).
- Camera/microphone/geolocation permission requests are denied at the engine
  level; the app holds no such Android permissions.
- WebView debugging is pinned to debug builds only.
- Backup and device-transfer rules exclude all app data.
- No analytics, no telemetry, no crash reporting, no ad feeds.

## Known limitations (honest list)

- Tracking protection is a baseline, not complete anti-tracking: Android
  WebView cannot intercept service workers, WebRTC or DNS-level tracking, and
  the blocklist is a periodic snapshot of a community source.
- The eTLD+1 approximation uses a small set of common two-part public suffixes.
- Cookies/site data and cache clearing are global (WebView scope), not per site.
- HTTPS encryption does not mean a site is trustworthy; no privacy scores are
  shown anywhere because they would be invented.
- The release keystore is committed to this repo on purpose so self-update
  signatures stay stable; acceptable for a personal/open project only.
- `usesCleartextTraffic` stays enabled so http:// pages can still load (with a
  visible "Not encrypted" state); the app's own API traffic is HTTPS-only.
- The AI agent backend is not implemented; the panel is development-only and
  clearly labeled as such.
