package dev.dkocaj.boppin;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;
import java.util.concurrent.CompletableFuture;

final class PendingPrompt {
    enum Kind { REGISTER, LOGIN }

    final Kind kind;
    final CompletableFuture<DialogResponseView> future;
    final PlayerConfigurationConnection conn;

    PendingPrompt(Kind kind, CompletableFuture<DialogResponseView> future,
                  PlayerConfigurationConnection conn) {
        this.kind = kind;
        this.future = future;
        this.conn = conn;
    }
}
