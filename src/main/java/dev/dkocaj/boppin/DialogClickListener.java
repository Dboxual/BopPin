package dev.dkocaj.boppin;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class DialogClickListener implements Listener {
    private final PreJoinListener preJoin;
    private final Logger log;

    public DialogClickListener(PreJoinListener preJoin, Logger log) {
        this.preJoin = preJoin;
        this.log = log;
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();

        boolean isSubmit = Dialogs.REGISTER_SUBMIT.equals(id) || Dialogs.LOGIN_SUBMIT.equals(id);
        boolean isCancel = Dialogs.REGISTER_CANCEL.equals(id) || Dialogs.LOGIN_CANCEL.equals(id);
        if (!isSubmit && !isCancel) return;

        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection conn)) {
            return;
        }

        UUID uuid = conn.getProfile().getId();
        if (uuid == null) return;

        Map<UUID, PendingPrompt> pending = preJoin.pending();
        PendingPrompt waiting = pending.get(uuid);
        if (waiting == null) {
            log.fine("[BopPin] click event with no pending prompt for " + uuid);
            return;
        }

        if (waiting.conn != conn) {
            log.warning("[BopPin] click event from different connection for " + uuid);
            return;
        }

        if (isCancel) {
            String kind = Dialogs.REGISTER_CANCEL.equals(id) ? "REGISTER" : "LOGIN";
            log.info("[BopPin] DIALOG CANCELLED kind=" + kind + " uuid=" + uuid);
            waiting.future.complete(null);
            return;
        }

        boolean expectRegister = Dialogs.REGISTER_SUBMIT.equals(id);
        PendingPrompt.Kind expected = expectRegister ? PendingPrompt.Kind.REGISTER
                                                     : PendingPrompt.Kind.LOGIN;
        if (waiting.kind != expected) {
            log.warning("[BopPin] click event kind mismatch for " + uuid
                    + " expected=" + waiting.kind + " got=" + expected);
            return;
        }

        waiting.future.complete(event.getDialogResponseView());
    }
}
