package de.xbrowniecodez.jbytemod.ui;

public final class NotificationManager {

    private NotificationManager() {
    }

    public static void showNotification(String text) {
        ToastManager.show(text, ToastManager.Kind.INFO);
    }
}
