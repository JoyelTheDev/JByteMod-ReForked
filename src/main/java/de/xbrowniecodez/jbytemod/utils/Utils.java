package de.xbrowniecodez.jbytemod.utils;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.utils.os.OSUtil;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

@UtilityClass
public class Utils {
    private static Properties cachedProperties;

    public Properties readPropertiesFile() {
        if (cachedProperties == null) {
            try (InputStream stream = Utils.class.getResourceAsStream("/resources/jbytemod.properties")) {
                Properties prop = new Properties();
                prop.load(stream);
                cachedProperties = prop;
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
        return cachedProperties;
    }

    public File getWorkingDirectory() {
        String userHome = System.getProperty("user.home", ".");
        String jbytePath = "JByteMod-ReForked/";
        File workingDirectory = switch (Objects.requireNonNull(OSUtil.getCurrentOS())) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield new File(appData != null ? appData : userHome, jbytePath);
            }
            case MAC -> new File(userHome, "Library/Application Support/" + jbytePath);
            default -> new File(userHome, jbytePath);
        };

        if (!workingDirectory.exists()) {
            if (!workingDirectory.mkdirs()) {
                Main.INSTANCE.getLogger().err("Failed to create working directory!");
                return new File(".");
            }
        }
        Main.INSTANCE.getLogger().log("Working directory " + workingDirectory);
        return workingDirectory;
    }
}
