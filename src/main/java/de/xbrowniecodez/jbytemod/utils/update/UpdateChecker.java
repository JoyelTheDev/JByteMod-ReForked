package de.xbrowniecodez.jbytemod.utils.update;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import javax.swing.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.utils.update.objects.Version;
import de.xbrowniecodez.jbytemod.utils.update.ui.UpdateDialogFrame;
import lombok.Getter;

@Getter
public class UpdateChecker {
    private Version latestVersion;

    public UpdateChecker() {
        new Thread(this::check).start();
    }

    public void check() {
        if (!Main.INSTANCE.getJByteMod().getOptions().get("check_update").getBoolean())
            return;
        Main.INSTANCE.getLogger().log("Checking for updates...");
        JsonObject releaseInfo = fetchLatestReleaseInfo();
        if (releaseInfo != null) {
            latestVersion = new Version(releaseInfo.get("name").getAsString());
            String changelog = releaseInfo.get("body").getAsString();
            if (latestVersion.isNewer(Main.INSTANCE.getJByteMod().getVersion()))
                showUpdateDialog(String.valueOf(latestVersion), changelog);
        }
    }
	
    private JsonObject fetchLatestReleaseInfo() {
        try {
            URL url = new URL("https://api.github.com/repos/JoyelTheDev/JByteMod-ReForked/releases/latest");
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (InputStream inputStream = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(inputStream)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            Main.INSTANCE.getLogger().warn("Update check failed: " + e.getMessage());
            return null;
        }
    }

    private void showUpdateDialog(String latestVersion, String changelog) {
        SwingUtilities.invokeLater(() -> {
            JFrame updateDialogFrame = new UpdateDialogFrame(latestVersion, changelog);
            updateDialogFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            updateDialogFrame.setSize(600, 500);
            updateDialogFrame.setLocationRelativeTo(null);
            updateDialogFrame.setVisible(true);
        });
    }
}
