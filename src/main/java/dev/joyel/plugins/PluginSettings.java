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
