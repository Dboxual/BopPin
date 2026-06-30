package dev.dkocaj.boppin;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class Dialogs {
    static final Key REGISTER_SUBMIT = Key.key("boppin", "register/submit");
    static final Key LOGIN_SUBMIT = Key.key("boppin", "login/submit");

    static final String INPUT_PIN = "pin";
    static final String INPUT_CONFIRM = "confirm";

    static final int PIN_MIN_LENGTH = 4;
    static final int PIN_MAX_LENGTH = 8;

    private Dialogs() {}

    static Dialog register(String playerName, String errorMessage) {
        Component title = Component.text("BopPin · Create PIN", NamedTextColor.AQUA);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(
                Component.text("Welcome, " + playerName + ".", NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(
                Component.text("Choose a numeric PIN ("
                        + PIN_MIN_LENGTH + "-" + PIN_MAX_LENGTH + " digits). "
                        + "You will use it every time you log in.", NamedTextColor.GRAY)));
        if (errorMessage != null) {
            body.add(DialogBody.plainMessage(
                    Component.text(errorMessage, NamedTextColor.RED)));
        }

        DialogBase base = DialogBase.builder(title)
                .body(body)
                .canCloseWithEscape(false)
                .pause(true)
                .inputs(List.of(
                        DialogInput.text(INPUT_PIN, Component.text("PIN", NamedTextColor.GREEN))
                                .width(200)
                                .maxLength(PIN_MAX_LENGTH)
                                .build(),
                        DialogInput.text(INPUT_CONFIRM, Component.text("Confirm PIN", NamedTextColor.GREEN))
                                .width(200)
                                .maxLength(PIN_MAX_LENGTH)
                                .build()
                ))
                .build();

        return Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Create PIN", NamedTextColor.GREEN),
                                Component.text("Save the PIN you just typed."),
                                150,
                                DialogAction.customClick(REGISTER_SUBMIT, null)
                        ),
                        ActionButton.create(
                                Component.text("Cancel", NamedTextColor.RED),
                                Component.text("Disconnect from the server."),
                                100,
                                null
                        )
                ))
        );
    }

    static Dialog login(String playerName, String errorMessage) {
        Component title = Component.text("BopPin · Enter PIN", NamedTextColor.AQUA);

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(
                Component.text("Welcome back, " + playerName + ".", NamedTextColor.WHITE)));
        body.add(DialogBody.plainMessage(
                Component.text("Enter your PIN to continue.", NamedTextColor.GRAY)));
        if (errorMessage != null) {
            body.add(DialogBody.plainMessage(
                    Component.text(errorMessage, NamedTextColor.RED)));
        }

        DialogBase base = DialogBase.builder(title)
                .body(body)
                .canCloseWithEscape(false)
                .pause(true)
                .inputs(List.of(
                        DialogInput.text(INPUT_PIN, Component.text("PIN", NamedTextColor.GREEN))
                                .width(200)
                                .maxLength(PIN_MAX_LENGTH)
                                .build()
                ))
                .build();

        return Dialog.create(b -> b.empty()
                .base(base)
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Log in", NamedTextColor.GREEN),
                                Component.text("Submit your PIN."),
                                150,
                                DialogAction.customClick(LOGIN_SUBMIT, null)
                        ),
                        ActionButton.create(
                                Component.text("Cancel", NamedTextColor.RED),
                                Component.text("Disconnect from the server."),
                                100,
                                null
                        )
                ))
        );
    }

    static boolean isValidPin(String pin) {
        if (pin == null) return false;
        int len = pin.length();
        if (len < PIN_MIN_LENGTH || len > PIN_MAX_LENGTH) return false;
        for (int i = 0; i < len; i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }
}
