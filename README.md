# BopPin

Pre-join PIN authentication for offline-mode Paper servers.

BopPin uses Paper's Dialog API to show a PIN dialog **during the configuration phase** — before the player enters any game world. This replaces the old chat-command approach (type `/login` after joining) with a proper GUI that blocks entry until authentication succeeds.

Works for both **Java** and **Bedrock** (via Geyser + Floodgate) players.

## How it works

### First-time registration

1. Player connects to the server.
2. A dialog appears asking them to create a 4–8 digit numeric PIN and confirm it.
3. The PIN is hashed (PBKDF2-HMAC-SHA256, 120k iterations) and saved to a local SQLite database, keyed by the player's offline-mode UUID.
4. The plugin verifies the saved PIN can be loaded back, then **disconnects the player** with: *"PIN created successfully. Please rejoin and enter your PIN."*

Registration does not grant server access — the player must rejoin.

### Returning login

1. Player connects.
2. A dialog appears asking for their PIN.
3. Correct PIN lets them into the server.
4. Wrong PIN allows up to 5 attempts before disconnect.

### Bedrock players

Bedrock players routed through Geyser + Floodgate get the same treatment automatically. Floodgate assigns a synthetic offline UUID; BopPin sees the same `AsyncPlayerConnectionConfigureEvent` and shows the same dialog. No special configuration needed.

### Fail-closed

Every code path that cannot determine whether to register or log in a player will **disconnect** them rather than allow unauthenticated entry.

## Requirements

| Requirement | Version |
|---|---|
| Paper | 26.1.2+ (Dialog API requires 1.21.7+) |
| Java | 25+ |
| Server mode | `online-mode=false` |

Optional: Geyser + Floodgate for Bedrock support.

## Installation

1. Build the jar (see below) or download from releases.
2. Place `BopPin-<version>.jar` in your server's `plugins/` folder.
3. Restart the server.
4. On first startup, Paper will automatically download `sqlite-jdbc` (declared in the plugin's `libraries:` block). A `plugins/BopPin/pins.sqlite` database file is created.
5. Players connecting will now see a PIN dialog before entering the game.

No configuration file is needed. PIN length (4–8 digits), attempt limits (3 register / 5 login), and dialog timeout (120 seconds) are compile-time defaults.

## Building

Requires JDK 25.

```bash
./gradlew build
```

The output jar is at `build/libs/BopPin-<version>.jar` (~21 KB). SQLite is not bundled — Paper resolves it at runtime via the `libraries:` mechanism in `plugin.yml`.

## Project structure

```
src/main/java/dev/dkocaj/boppin/
  BopPinPlugin.java        Entry point. Opens DB, registers listeners.
  PreJoinListener.java     Handles AsyncPlayerConnectionConfigureEvent.
                           Register/login loops with CompletableFuture blocking.
  DialogClickListener.java Handles PlayerCustomClickEvent. Completes futures.
  Dialogs.java             Builds register/login Dialog objects. PIN validation.
  PendingPrompt.java       Value object: prompt kind + future.
  PinStore.java            SQLite storage (UUID-keyed, WAL mode).
  PinHasher.java           PBKDF2 hashing + constant-time verify.
```

## Known limitations

- No PIN reset (admin command or self-service).
- No rate limiting between connection attempts (only within a single session).
- No config file — all constants are compile-time.
- Registration requires disconnect + rejoin (by design).
- PIN input is not masked (Minecraft has no password-field widget).
- No premium/online-mode bypass.
- Single-server only (no cross-server PIN sync).

## License

[MIT](LICENSE)
