package dev.joyel.update;

import de.xbrowniecodez.jbytemod.Main;

import javax.swing.*;

public final class UpdateService {

    private UpdateService() {}

    public static void checkAsync(String currentVersion) {
        if (currentVersion == null || currentVersion.isBlank()) return;
        if (!isEnabled()) {
            Main.INSTANCE.getLogger().log("Update check disabled in options.");
            return;
        }

        Thread t = new Thread(() -> {
            Main.INSTANCE.getLogger().log("Checking for updates (semver)...");
            UpdateRelease release;
            try {
                release = UpdateChecker.checkForUpdate(currentVersion);
            } catch (Exception e) {
                Main.INSTANCE.getLogger().warn("Update check failed: " + e.getMessage());
                return;
            }

            if (release == null) {
                Main.INSTANCE.getLogger().log("No update available. Running " + currentVersion + ".");
                return;
            }

            Main.INSTANCE.getLogger().log("Update available: " + release.getVersion() + " (current: " + currentVersion + ")");
            SwingUtilities.invokeLater(() -> UpdateDialogFrame.show(release));
        }, "JByteMod-UpdateChecker");
        t.setDaemon(true);
        t.start();
    }

    private static boolean isEnabled() {
        try {
            return Main.INSTANCE.getJByteMod().getOptions().get("check_update").getBoolean();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
