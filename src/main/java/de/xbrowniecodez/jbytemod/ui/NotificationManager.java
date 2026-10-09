package de.xbrowniecodez.jbytemod.ui;

import dev.joyel.ui.ToastManager;

public final class NotificationManager {

    private NotificationManager() {
    }

    public static void showNotification(String text) {
        ToastManager.show(text, ToastManager.Kind.INFO);
    }
}
