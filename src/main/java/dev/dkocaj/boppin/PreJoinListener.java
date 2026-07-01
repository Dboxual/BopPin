package dev.dkocaj.boppin;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class PreJoinListener implements Listener {
    static final String TAG = "[BopPin]";

    private static final int REGISTER_MAX_ATTEMPTS = 3;
    private static final int LOGIN_MAX_ATTEMPTS = 5;
    private static final long DIALOG_TIMEOUT_SECONDS = 120;
    private static final long ONLINE_CHECK_TIMEOUT_SECONDS = 5;

    private final PinStore store;
    private final Logger log;
    private final Plugin plugin;
    private final List<String> bedrockPrefixes;
    private final Map<UUID, PendingPrompt> pending = new ConcurrentHashMap<>();
    private final Set<UUID> activePreAuth = ConcurrentHashMap.newKeySet();

    public PreJoinListener(PinStore store, Logger log, Plugin plugin,
                           List<String> bedrockPrefixes) {
        this.store = store;
        this.log = log;
        this.plugin = plugin;
        this.bedrockPrefixes = bedrockPrefixes;
    }

    Map<UUID, PendingPrompt> pending() {
        return pending;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        Object rawConn = event.getConnection();
        String connClass = rawConn == null ? "null" : rawConn.getClass().getName();
        say("CONFIG EVENT FIRED  connection=" + connClass);

        if (!(rawConn instanceof PlayerConfigurationConnection conn)) {
            say("FAIL-CLOSED: connection is not PlayerConfigurationConnection (" + connClass + ")");
            failClosed(rawConn, "BopPin: unexpected connection type during config phase.");
            return;
        }

        PlayerProfile profile = conn.getProfile();
        UUID uuid = profile == null ? null : profile.getId();
        String name = profile == null ? null : profile.getName();
        say("profile name=" + name + " uuid=" + uuid);

        if (uuid == null) {
            say("FAIL-CLOSED: UUID is null for name=" + name);
            disconnect(conn, "BopPin: could not resolve your offline UUID. Reconnect or contact admin.");
            return;
        }
        String safeName = name != null ? name : uuid.toString();
        String canonical = canonicalName(safeName);
        say("canonical name=" + canonical + " (raw=" + safeName + ")");

        // --- Duplicate online check (dispatched to main thread) ---
        boolean online;
        try {
            online = isOnline(uuid, name);
        } catch (Exception e) {
            say("FAIL-CLOSED: isOnline check failed for " + safeName + ": " + e);
            disconnect(conn, "BopPin: could not verify session state. Reconnect.");
            return;
        }
        say("ONLINE DUPLICATE CHECK uuid=" + uuid + " name=" + safeName + " result=" + online);

        if (online) {
            say("DUPLICATE ONLINE ACCOUNT BLOCKED name=" + safeName + " uuid=" + uuid);
            disconnect(conn,
                    "This account is already online. If this is you, log out first before reconnecting.");
            return;
        }

        // --- Pre-auth UUID guard (prevents two config-phase connections racing) ---
        if (!activePreAuth.add(uuid)) {
            say("ACTIVE PREAUTH DUPLICATE BLOCKED name=" + safeName + " uuid=" + uuid);
            disconnect(conn,
                    "This account is already logging in. Wait a moment and try again.");
            return;
        }

        try {
            boolean uuidRegistered;
            try {
                uuidRegistered = store.hasPin(uuid);
            } catch (RuntimeException e) {
                say("FAIL-CLOSED: hasPin(" + uuid + ") threw: " + e);
                disconnect(conn, "BopPin: database error. Please contact the admin.");
                return;
            }
            say("hasPin(" + uuid + ") = " + uuidRegistered);

            UUID canonicalOwner;
            try {
                canonicalOwner = store.findOwnerByCanonicalName(canonical);
            } catch (RuntimeException e) {
                say("FAIL-CLOSED: findOwnerByCanonicalName(" + canonical + ") threw: " + e);
                disconnect(conn, "BopPin: database error. Please contact the admin.");
                return;
            }
            say("findOwnerByCanonicalName(" + canonical + ") = " + canonicalOwner);

            // Decision logic
            if (uuidRegistered) {
                say("decision=LOGIN uuid=" + uuid + " name=" + safeName);
                runLogin(conn, uuid, safeName, canonical);
            } else if (canonicalOwner != null) {
                if (canonicalOwner.equals(uuid)) {
                    say("decision=LOGIN (canonical match, same UUID) uuid=" + uuid
                            + " name=" + safeName + " canonical=" + canonical);
                    runLogin(conn, uuid, safeName, canonical);
                } else {
                    say("REGISTER BLOCKED: canonical name already registered"
                            + " name=" + safeName
                            + " canonical=" + canonical
                            + " currentUuid=" + uuid
                            + " ownerUuid=" + canonicalOwner);
                    disconnect(conn,
                            "This name is already registered. "
                            + "Reconnect with the original account/name or contact staff.");
                }
            } else {
                say("decision=REGISTER uuid=" + uuid + " name=" + safeName
                        + " canonical=" + canonical);
                runRegister(conn, uuid, safeName, canonical);
            }
        } catch (RuntimeException e) {
            say("FAIL-CLOSED: pre-join flow crashed for " + safeName + ": " + e);
            disconnect(conn, "BopPin: internal error during pre-join.");
        } finally {
            activePreAuth.remove(uuid);
            PendingPrompt p = pending.get(uuid);
            if (p != null && p.conn == conn) {
                pending.remove(uuid);
            }
        }
    }

    @EventHandler
    public void onClose(PlayerConnectionCloseEvent event) {
        UUID uuid = event.getPlayerUniqueId();
        say("CONNECTION CLOSED  name=" + event.getPlayerName() + " uuid=" + uuid);
        PendingPrompt p = pending.remove(uuid);
        if (p != null) {
            p.future.complete(null);
        }
    }

    String canonicalName(String rawName) {
        String stripped = rawName;
        for (String prefix : bedrockPrefixes) {
            if (stripped.startsWith(prefix) && stripped.length() > prefix.length()) {
                stripped = stripped.substring(prefix.length());
                break;
            }
        }
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isOnline(UUID uuid, String name) throws Exception {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getUniqueId().equals(uuid)) {
                        future.complete(true);
                        return;
                    }
                    if (name != null && p.getName().equalsIgnoreCase(name)) {
                        future.complete(true);
                        return;
                    }
                }
                future.complete(false);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(ONLINE_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void runRegister(PlayerConfigurationConnection conn, UUID uuid,
                             String name, String canonical) {
        String error = null;
        for (int attempt = 1; attempt <= REGISTER_MAX_ATTEMPTS; attempt++) {
            say("REGISTER DIALOG SHOWN to " + name + " (attempt " + attempt + ")");
            DialogResponseView view = prompt(conn, uuid, PendingPrompt.Kind.REGISTER,
                    Dialogs.register(name, error));

            if (view == null) {
                say("DIALOG CANCELLED kind=REGISTER name=" + name + " uuid=" + uuid);
                say("DISCONNECTING AFTER CANCEL");
                disconnect(conn, "PIN setup cancelled. Reconnect when you are ready to create a PIN.");
                return;
            }

            String pin = view.getText(Dialogs.INPUT_PIN);
            String confirm = view.getText(Dialogs.INPUT_CONFIRM);
            say("register: response received pinLen="
                    + (pin == null ? -1 : pin.length()) + " confirmLen="
                    + (confirm == null ? -1 : confirm.length()));

            if (!Dialogs.isValidPin(pin)) {
                error = "PIN must be " + Dialogs.PIN_MIN_LENGTH + "-" + Dialogs.PIN_MAX_LENGTH
                        + " digits, numbers only.";
                say("register: invalid PIN format from " + name);
                continue;
            }
            if (!pin.equals(confirm)) {
                error = "Your two PINs did not match. Try again.";
                say("register: PIN/confirm mismatch from " + name);
                continue;
            }

            try {
                store.savePin(uuid, name, canonical, pin);
            } catch (SQLException e) {
                say("register: SAVE FAILED for " + name + ": " + e.getMessage());
                disconnect(conn, "Server failed to save your PIN. Contact admin.");
                return;
            }
            say("PIN SAVED for " + name + " (" + uuid + ") canonical=" + canonical);

            if (!store.verifyPin(uuid, pin)) {
                say("register: POST-SAVE VERIFY FAILED for " + name);
                disconnect(conn, "Server could not verify the saved PIN. Contact admin.");
                return;
            }
            say("PIN VERIFIED for " + name + " (" + uuid + ")");

            say("DISCONNECT-AFTER-REGISTER for " + name);
            disconnect(conn, "PIN created successfully. Please rejoin and enter your PIN.");
            return;
        }

        say("register: exhausted attempts for " + name);
        disconnect(conn, "Too many invalid attempts. Reconnect to try again.");
    }

    private void runLogin(PlayerConfigurationConnection conn, UUID uuid,
                          String name, String canonical) {
        String error = null;
        for (int attempt = 1; attempt <= LOGIN_MAX_ATTEMPTS; attempt++) {
            say("LOGIN DIALOG SHOWN to " + name + " (attempt " + attempt + ")");
            DialogResponseView view = prompt(conn, uuid, PendingPrompt.Kind.LOGIN,
                    Dialogs.login(name, error));

            if (view == null) {
                say("DIALOG CANCELLED kind=LOGIN name=" + name + " uuid=" + uuid);
                say("DISCONNECTING AFTER CANCEL");
                disconnect(conn, "PIN login cancelled. Reconnect when you are ready to log in.");
                return;
            }

            String pin = view.getText(Dialogs.INPUT_PIN);
            say("login: response received pinLen=" + (pin == null ? -1 : pin.length()));

            if (!Dialogs.isValidPin(pin)) {
                error = "PIN format invalid (" + Dialogs.PIN_MIN_LENGTH + "-"
                        + Dialogs.PIN_MAX_LENGTH + " digits).";
                say("login: invalid PIN format from " + name);
                continue;
            }

            if (store.verifyPin(uuid, pin)) {
                store.touchName(uuid, name, canonical);
                say("LOGIN SUCCESS for " + name + " (" + uuid + ")");
                return;
            }

            error = "Wrong PIN. " + (LOGIN_MAX_ATTEMPTS - attempt) + " attempt(s) left.";
            say("LOGIN FAILURE (wrong pin) for " + name + " attempt " + attempt);
        }

        say("login: exhausted attempts for " + name);
        disconnect(conn, "Too many invalid PIN attempts.");
    }

    private DialogResponseView prompt(PlayerConfigurationConnection conn, UUID uuid,
                                     PendingPrompt.Kind kind, Dialog dialog) {
        CompletableFuture<DialogResponseView> future = new CompletableFuture<>();
        future.completeOnTimeout(null, DIALOG_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        PendingPrompt prev = pending.put(uuid, new PendingPrompt(kind, future, conn));
        if (prev != null) {
            prev.future.complete(null);
        }

        try {
            conn.getAudience().showDialog(dialog);
        } catch (RuntimeException e) {
            say("prompt: showDialog threw for " + uuid + ": " + e);
            future.complete(null);
        }

        try {
            return future.join();
        } catch (RuntimeException e) {
            say("prompt: future.join threw for " + uuid + ": " + e);
            return null;
        }
    }

    private void disconnect(PlayerConfigurationConnection conn, String message) {
        try {
            conn.disconnect(Component.text(message, NamedTextColor.RED));
        } catch (RuntimeException e) {
            say("disconnect: threw on " + message + " -> " + e);
        }
    }

    private void failClosed(Object rawConn, String message) {
        if (rawConn instanceof PlayerConnection pc) {
            try {
                pc.disconnect(Component.text(message, NamedTextColor.RED));
                return;
            } catch (RuntimeException e) {
                say("failClosed: disconnect via PlayerConnection threw: " + e);
            }
        }
        say("failClosed: could not disconnect; rawConn=" + rawConn);
    }

    private void say(String msg) {
        log.info(TAG + " " + msg);
    }
}
