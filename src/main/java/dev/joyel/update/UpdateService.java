package dev.joyel.update;

import de.xbrowniecodez.jbytemod.Main;
import javax.swing.*;

public final class UpdateService {

    private UpdateService() {}

    public static void checkAsync(String currentVersion) {
        if (currentVersion == null || currentVersion.trim().isEmpty()) return;

        Thread t = new Thread(new Runnable() {
            public void run() {
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

                Main.INSTANCE.getLogger().log(
                        "Update available: " + release.getVersion() + " (current: " + currentVersion + ")");

                final UpdateRelease finalRelease = release;
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        UpdateDialogFrame.show(finalRelease);
                    }
                });
            }
        }, "JByteMod-UpdateChecker");
        t.setDaemon(true);
        t.start();
    }
}
