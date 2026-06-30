# Changelog

## 0.1.2 — 2026-06-30

First working release. Pre-join PIN authentication confirmed working on a live
Paper 26.1.2 (build 72) offline-mode server with both Java and Bedrock
(Floodgate) players.

### What works

- **Pre-join registration dialog** — first-time players see a Paper Dialog
  during the configuration phase asking them to create a 4–8 digit numeric PIN.
  PIN and confirmation must match. After successful registration the player is
  disconnected with "PIN created successfully. Please rejoin and enter your PIN."
- **Pre-join login dialog** — returning players see a login dialog. Correct PIN
  lets them into the server; wrong PIN allows up to 5 attempts before disconnect.
- **Bedrock support via Floodgate** — Bedrock players routed through
  Geyser + Floodgate are identified by their Floodgate-prefixed offline UUID
  (e.g. `00000000-0000-0000-0009-…`). The same `AsyncPlayerConnectionConfigureEvent`
  fires for them, the same dialog is shown, and PIN registration/login works
  identically.
- **PIN hashing** — PINs are hashed with PBKDF2-HMAC-SHA256 (120 000 iterations,
  16-byte random salt, 256-bit derived key). Constant-time comparison on verify.
- **SQLite storage** — single `pins.sqlite` file in the plugin data folder.
  WAL journal mode. Keyed by canonical offline-mode UUID; player name stored
  only for display/logging.
- **Fail-closed** — every code path that cannot determine whether to show
  register or login disconnects the player rather than allowing unauthenticated
  entry.
- **Verbose diagnostic logging** — every decision point logs with `[BopPin]`
  prefix: startup banner, CONFIG EVENT FIRED, hasPin result, dialog shown,
  PIN saved/verified, login success/failure, disconnect reasons, connection close.

### Bug fixes during development

- **v0.1.0 → 0.1.1**: Switched from `paper-plugin.yml` to standard `plugin.yml`
  (Bukkit format) because the paper-plugin loader silently rejected the plugin.
  Added fail-closed logic and diagnostic logging at every return path.
- **v0.1.1 → 0.1.2**: Fixed `UnsatisfiedLinkError` crash on startup caused by
  shadow-jar relocating `org.sqlite` → `dev.dkocaj.boppin.libs.sqlite`. The JNI
  native symbols in sqlite-jdbc are hardcoded to `org.sqlite.core.NativeDB` and
  cannot be relocated. Fix: stopped bundling sqlite-jdbc; use Paper's `libraries:`
  mechanism instead, which downloads it at runtime into an isolated classloader.
  Also fixed stale version in embedded `plugin.yml` (Gradle `processResources`
  was not re-expanding `${version}` on incremental builds).

### Known limitations

- No PIN reset mechanism (admin or self-service).
- No rate limiting between connection attempts (only per-connection attempt limits).
- No config file — PIN length, attempt limits, and timeout are compile-time constants.
- Registration requires the player to disconnect and rejoin (by design for Phase 1).
- Dialog text inputs are not masked (Minecraft text inputs have no password mode).
