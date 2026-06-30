package dev.dkocaj.boppin;

import io.papermc.paper.dialog.DialogResponseView;
import java.util.concurrent.CompletableFuture;

final class PendingPrompt {
    enum Kind { REGISTER, LOGIN }

    final Kind kind;
    final CompletableFuture<DialogResponseView> future;

    PendingPrompt(Kind kind, CompletableFuture<DialogResponseView> future) {
        this.kind = kind;
        this.future = future;
    }
}
