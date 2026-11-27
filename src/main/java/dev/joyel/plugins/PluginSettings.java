package dev.joyel.plugins;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class PluginSettings {
    private static final String ENABLED_PREFIX = "enabled.";
    private static final String INFO_PREFIX = "info.";
    private static final String REPOSITORIES_KEY = "repositories";
    private static final int MAX_VALUE = 7500;
    private static final int MAX_KEY = 70;
    private static final int MAX_NODE = 70;
    private static final String TRUSTED_KEYS = "trustedKeys";
    private static final String PUBLISHER_PREFIX = "publisher.";
    private static final String NOTIFIED_KEY = "notifiedUpdates";
    private static final String UPDATE_CHECKS_KEY = "updateChecks";
    private static final int MAX_TRUSTED = 150;
    private static final int MAX_NOTIFIED = 100;

    private final Preferences preferences;

    public PluginSettings(Preferences preferences) {
        this.preferences = preferences;
    }

    public static PluginSettings system() {
        return new PluginSettings(Preferences.userNodeForPackage(PluginSettings.class));
    }

    public boolean isEnabled(String key) {
        return preferences.getBoolean(keyFor(ENABLED_PREFIX, key), true);
    }

    public void setEnabled(String key, boolean enabled) {
        preferences.putBoolean(keyFor(ENABLED_PREFIX, key), enabled);
        flush();
    }

    public void forget(String key) {
        preferences.remove(keyFor(ENABLED_PREFIX, key));
        preferences.remove(keyFor(INFO_PREFIX, key));
        flush();
    }

    public void cacheInfo(String key, String name, String version, String author, String description) {
        String value = join(clip(name, 200), clip(version, 100), clip(author, 200), clip(description, 1000));
        if (value.length() > MAX_VALUE) {
            value = join(clip(name, 200), clip(version, 100), clip(author, 200), "");
        }
        if (value.length() <= MAX_VALUE) {
            preferences.put(keyFor(INFO_PREFIX, key), value);
            flush();
        }
    }

    public String[] cachedInfo(String key) {
        String stored = preferences.get(keyFor(INFO_PREFIX, key), null);
        if (stored == null) {
            return null;
        }
        String[] parts = stored.split(",", -1);
        if (parts.length != 4) {
            return null;
        }
        String[] result = new String[4];
        for (int index = 0; index < 4; index++) {
            result[index] = decode(parts[index]);
        }
        return result;
    }

    public List<String> repositories() {
        List<String> result = new ArrayList<>();
        String stored = preferences.get(REPOSITORIES_KEY, "");
        if (stored.isEmpty()) {
            return result;
        }
        for (String part : stored.split(",")) {
            String decoded = decode(part).trim();
            if (!decoded.isEmpty()) {
                result.add(decoded);
            }
        }
        return result;
    }

    public void saveRepositories(Set<String> repositories) {
        List<String> encoded = new ArrayList<>();
        for (String repository : new LinkedHashSet<>(repositories)) {
            encoded.add(URLEncoder.encode(repository, StandardCharsets.UTF_8));
        }
        String value = String.join(",", encoded);
        if (value.length() > MAX_VALUE) {
            throw new IllegalArgumentException("Too many or too long repository entries");
        }
        preferences.put(REPOSITORIES_KEY, value);
        flush();
    }

    public boolean updateChecksEnabled() {
        return preferences.getBoolean(UPDATE_CHECKS_KEY, true);
    }

    public void setUpdateChecksEnabled(boolean enabled) {
        preferences.putBoolean(UPDATE_CHECKS_KEY, enabled);
        flush();
    }

    public Set<String> trustedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        String stored = preferences.get(TRUSTED_KEYS, "");
        if (!stored.isEmpty()) {
            for (String part : stored.split(",")) {
                String decoded = decode(part).trim();
                if (!decoded.isEmpty()) {
                    keys.add(decoded);
                }
            }
        }
        return keys;
    }

    public boolean isTrustedKey(String fingerprint) {
        return fingerprint != null && trustedKeys().contains(fingerprint);
    }

    public void trustKey(String fingerprint) {
        Set<String> keys = trustedKeys();
        if (keys.size() >= MAX_TRUSTED && !keys.contains(fingerprint)) {
            throw new IllegalStateException("Too many trusted publishers");
        }
        keys.add(fingerprint);
        saveList(TRUSTED_KEYS, keys);
    }

    public void untrustKey(String fingerprint) {
        Set<String> keys = trustedKeys();
        if (keys.remove(fingerprint)) {
            saveList(TRUSTED_KEYS, keys);
        }
    }

    public String publisher(String pluginId) {
        String value = preferences.get(keyFor(PUBLISHER_PREFIX, pluginId), "");
        return value.isEmpty() ? null : value;
    }

    public void setPublisher(String pluginId, String fingerprint) {
        preferences.put(keyFor(PUBLISHER_PREFIX, pluginId), fingerprint);
        flush();
    }

    public void forgetPublisher(String pluginId) {
        preferences.remove(keyFor(PUBLISHER_PREFIX, pluginId));
        flush();
    }

    public boolean wasNotified(String marker) {
        String stored = preferences.get(NOTIFIED_KEY, "");
        if (stored.isEmpty()) {
            return false;
        }
        for (String part : stored.split(",")) {
            if (decode(part).equals(marker)) {
                return true;
            }
        }
        return false;
    }

    public void markNotified(String marker) {
        List<String> markers = new ArrayList<>();
        String stored = preferences.get(NOTIFIED_KEY, "");
        if (!stored.isEmpty()) {
            for (String part : stored.split(",")) {
                markers.add(decode(part));
            }
        }
        if (!markers.contains(marker)) {
            markers.add(marker);
        }
        while (markers.size() > MAX_NOTIFIED) {
            markers.remove(0);
        }
        saveList(NOTIFIED_KEY, markers);
    }

    public String pluginSetting(String pluginId, String key, String defaultValue) {
        String stored = pluginNode(pluginId).get(shortKey(key), null);
        return stored == null ? defaultValue : decode(stored);
    }

    public void putPluginSetting(String pluginId, String key, String value) {
        if (value == null) {
            throw new IllegalArgumentException("A setting value cannot be null");
        }
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        if (encoded.length() > MAX_VALUE) {
            throw new IllegalArgumentException("The setting value is too long");
        }
        pluginNode(pluginId).put(shortKey(key), encoded);
        flushNode(pluginId);
    }

    public void removePluginSetting(String pluginId, String key) {
        pluginNode(pluginId).remove(shortKey(key));
        flushNode(pluginId);
    }

    private Preferences pluginNode(String pluginId) {
        String name = URLEncoder.encode(pluginId, StandardCharsets.UTF_8).replace('/', '_');
        if (name.length() > MAX_NODE) {
            name = Integer.toHexString(pluginId.hashCode()) + "." + name.substring(name.length() - 40);
        }
        return preferences.node("plugin-data").node(name);
    }

    private void flushNode(String pluginId) {
        try {
            pluginNode(pluginId).flush();
        } catch (BackingStoreException ignored) {
        }
    }

    private static String shortKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("A setting key cannot be empty");
        }
        String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return encoded.length() <= 80 ? encoded : Integer.toHexString(key.hashCode()) + "." + encoded.substring(encoded.length() - 60);
    }

    private void saveList(String key, java.util.Collection<String> values) {
        List<String> encoded = new ArrayList<>();
        for (String value : values) {
            encoded.add(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        preferences.put(key, String.join(",", encoded));
        flush();
    }

    private static String keyFor(String prefix, String key) {
        String full = prefix + URLEncoder.encode(key, StandardCharsets.UTF_8);
        if (full.length() <= MAX_KEY) {
            return full;
        }
        return prefix + Integer.toHexString(key.hashCode()) + "." + URLEncoder.encode(key.substring(key.length() - 30), StandardCharsets.UTF_8);
    }

    private static String join(String... fields) {
        List<String> encoded = new ArrayList<>();
        for (String field : fields) {
            encoded.add(URLEncoder.encode(field, StandardCharsets.UTF_8));
        }
        return String.join(",", encoded);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException failure) {
            return "";
        }
    }

    private static String clip(String value, int limit) {
        String text = value == null ? "" : value;
        return text.length() > limit ? text.substring(0, limit) : text;
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (BackingStoreException ignored) {
        }
    }
}
