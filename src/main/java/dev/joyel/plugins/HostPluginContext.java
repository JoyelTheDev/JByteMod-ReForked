package dev.joyel.plugins;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class HostPluginContext implements PluginContext {
    private final String id;
    private final Path dataRoot;
    private final PluginSettings settings;
    private final Supplier<String> applicationVersion;
    private final Consumer<String> logger;
    private final Consumer<String> toaster;

    HostPluginContext(String id, Path dataRoot, PluginSettings settings, Supplier<String> applicationVersion,
                      Consumer<String> logger, Consumer<String> toaster) {
        this.id = id;
        this.dataRoot = dataRoot;
        this.settings = settings;
        this.applicationVersion = applicationVersion;
        this.logger = logger;
        this.toaster = toaster;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String applicationVersion() {
        return applicationVersion == null ? "" : applicationVersion.get();
    }

    @Override
    public Path dataFolder() {
        String safe = id.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty() || safe.startsWith(".")) {
            safe = "plugin" + safe;
        }
        Path folder = dataRoot.resolve(safe);
        try {
            Files.createDirectories(folder);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not create the plugin data folder", failure);
        }
        return folder;
    }

    @Override
    public String setting(String key, String defaultValue) {
        return settings.pluginSetting(id, key, defaultValue);
    }

    @Override
    public void saveSetting(String key, String value) {
        settings.putPluginSetting(id, key, value);
    }

    @Override
    public void removeSetting(String key) {
        settings.removePluginSetting(id, key);
    }

    @Override
    public void log(String message) {
        if (logger != null) {
            logger.accept("[" + id + "] " + message);
        }
    }

    @Override
    public void toast(String message) {
        if (toaster != null) {
            toaster.accept(message);
        }
    }
}
