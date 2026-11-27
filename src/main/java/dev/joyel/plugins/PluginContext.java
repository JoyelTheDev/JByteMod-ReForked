package dev.joyel.plugins;

import java.nio.file.Path;

public interface PluginContext {
    String id();

    String applicationVersion();

    Path dataFolder();

    String setting(String key, String defaultValue);

    void saveSetting(String key, String value);

    void removeSetting(String key);

    void log(String message);

    void toast(String message);
}
