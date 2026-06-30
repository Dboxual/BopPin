package dev.dkocaj.boppin;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
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
        boolean isRegister = Dialogs.REGISTER_SUBMIT.equals(event.getIdentifier());
        boolean isLogin = Dialogs.LOGIN_SUBMIT.equals(event.getIdentifier());
        if (!isRegister && !isLogin) return;

        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection conn)) {
            return;
        }

        UUID uuid = conn.getProfile().getId();
        if (uuid == null) return;

        DialogResponseView view = event.getDialogResponseView();
        Map<UUID, PendingPrompt> pending = preJoin.pending();
        PendingPrompt waiting = pending.get(uuid);
        if (waiting == null) {
            log.fine("[BopPin] click event with no pending prompt for " + uuid);
            return;
        }

        PendingPrompt.Kind expected = isRegister ? PendingPrompt.Kind.REGISTER
                                                 : PendingPrompt.Kind.LOGIN;
        if (waiting.kind != expected) {
            log.warning("[BopPin] click event kind mismatch for " + uuid
                    + " expected=" + waiting.kind + " got=" + expected);
            return;
        }

        waiting.future.complete(view);
    }
}
