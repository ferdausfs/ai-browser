# AI Browser

An Android browser with an AI command bar — familiar browser UI, with an AI agent that
(observed over the coming phases) can read pages, plan actions and execute them safely
under your approval.

Built with **Kotlin + Jetpack Compose (Material 3)**, **Android WebView**, **Room**,
**ViewModel/StateFlow/Coroutines**, and a provider-agnostic **AI layer** (mock provider
included, so the app runs fully offline without any API key).

---

## Status: Phase 1 shipped (v1.0.0)

### Implemented in this release

**Browser (fully working):**
- Address/search bar with smart URL detection, security indicator (lock / not-secure) and
  bookmark/history suggestions while typing
- Back / forward / reload / stop, live page-load progress bar
- Multi-tab browsing (open, switch, close, tab count badge), tabs persist across restarts
- Bookmarks and history (Room database, local-only)
- Clear browsing data: history, cookies & site data, cache
- Polished Material 3 light/dark theme (follows system, or override in Settings)
- Start page with quick links, bottom toolbar, snackbar notifications

**In-app Self-Update (fully working):**
- Checks its own GitHub Releases (`releases/latest`), compares semantic versions
- One tap: download with progress -> hand APK to the platform installer
- Respects the "Install unknown apps" consent flow; no credentials embedded
- Auto-check once per 24h (toggle in Settings), manual check in Settings -> Updates

**AI agent contract (interfaces + mock, no UI-blockers):**
- Full typed action vocabulary: `observe_page`, `scroll`, `click_element`, `fill_field`,
  `navigate`, `go_back`, `ask_user`, `finish` (`AgentAction` sealed class)
- `AiProvider`, `ActionExecutor`, `ActionVerifier`, `ApprovalHandler` interfaces
- Visible AI command panel wired end-to-end with the Mock provider

### Planned (Phase 2-4)

| Phase | Scope |
|-------|-------|
| 2 | Read-only page observation (compact page representation, privacy redaction) + real AI summarization backend |
| 3 | Controlled navigation/scroll/click/form-fill with typed actions, verification, visible agent status & logs, step limits, loop detection |
| 4 | Assisted login (pause on CAPTCHA/OTP/passkey), approval gates for purchases/posts/deletions, hardening + full test suite |

---

## Architecture

```
com.zai.aibrowser
├── AiBrowserApp / MainActivity        # single activity, Compose
├── browser/                           # Layer 1: browser engine + tab registry
│   ├── TabManager / BrowserTab        # per-tab state flows (url, progress, back/forward...)
│   ├── SecureWebViewFactory           # hardening + WebViewClient/WebChromeClient wiring
│   └── UrlNormalizer                  # single auditable "search vs navigate" decision
├── data/                              # Room storage (tabs, bookmarks, history)
│   ├── db/ (Entities, Daos, AppDatabase)
│   └── BrowserRepository              # single door to all browser data
├── ai/                                # Layers 2-6 contract (Phase 1: mock implementation)
│   ├── AiModels.kt                    # AgentAction schema, PageObservation, approvals
│   └── AiProvider.kt                  # AiProvider / ActionExecutor / ActionVerifier / ApprovalHandler
├── update/                            # self-update: GitHub Releases check, download, install
└── ui/                                # Compose screens, sheets, theme, ViewModels
```

Agent layers (stable interfaces today, real implementations in Phase 2-4):

```
[Browser UI/Tables] -> [PageObserver] -> [AiProvider/planner] -> [ActionExecutor]
                                                        |                |
                                                 [ApprovalHandler]  [ActionVerifier]
```

## Security & privacy posture (already enforced in Phase 1)

- **No JavaScript bridge**: `addJavascriptInterface()` is never used — page content can
  never reach app internals. Page content is treated as untrusted data, not instructions.
- **SSL errors are never bypassed**; an error page explains and blocks.
- `file://`, `content://`, `data:`, `blob:`, `javascript:` navigations are blocked;
  unknown external schemes are not auto-fired (no arbitrary intent launching).
- Safe Browsing on, mixed content never allowed, geolocation off, no file access.
- **No broad permissions**: only `INTERNET`, `ACCESS_NETWORK_STATE`, and
  `REQUEST_INSTALL_PACKAGES` (for self-update, user-approved). No Accessibility Service.
- **No AI credentials in the app**; the update checker is unauthenticated over HTTPS.
- Browsing data never leaves the device; `allowBackup=false`.
- Release signing keystore is committed **by design** so future releases keep the same
  signature (required for updates to install). Fine for a personal/open project.

## Self-Update — how it works

1. App calls `https://api.github.com/repos/ferdausfs/ai-browser/releases/latest`
2. Compares release tag `vX.Y.Z` with installed `versionName` (semantic compare)
3. If newer: banner/dialog or Settings -> Updates -> "Download and install"
4. APK downloads to app cache with a progress bar, then the platform installer opens
5. User approves; Android enforces the same-signature rule

## How to release an update (zero-hassle future updates)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`
2. Commit and push to `main`
3. Tag and push the tag — CI does the rest:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
4. The `Release` workflow (`.github/workflows/release.yml`) builds the signed release APK
   and publishes a GitHub Release with it
5. Installed apps pick it up via the built-in updater (auto-check or manual)

## Build it yourself

Requirements: JDK 17+, Android SDK (platform 34, build-tools 34.0.0).

```bash
git clone https://github.com/ferdausfs/ai-browser.git
cd ai-browser
# point local.properties at your SDK (create the file):
#   sdk.dir=/path/to/android-sdk
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

The release keystore + password live in `keystore/` (committed intentionally — see the
security note above).

## Install

1. Download `ai-browser-vX.Y.Z.apk` from [Releases](https://github.com/ferdausfs/ai-browser/releases)
2. Open it; Android asks to allow installs from your browser/file manager (one time)
3. Install — done. Future versions update in-app.
