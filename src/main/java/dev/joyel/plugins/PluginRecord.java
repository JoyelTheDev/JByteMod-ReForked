package dev.joyel.plugins;

import de.xbrowniecodez.jbytemod.plugin.Plugin;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class PluginRecord {
    public enum State {
        ACTIVE("Active"),
        DISABLED("Disabled"),
        FAILED("Failed");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    final String key;
    final boolean builtin;
    final Path file;
    final Supplier<Plugin> factory;
    final List<Plugin> plugins = new ArrayList<>();
    URLClassLoader loader;
    boolean enabled = true;
    String error;
    String manifestId;
    List<String> classNames = List.of();
    String name;
    String version = "";
    String author = "";
    String description = "";
    int faults;
    int slowCalls;
    String lastFault;

    PluginRecord(String key, boolean builtin, Path file, Supplier<Plugin> factory) {
        this.key = key;
        this.builtin = builtin;
        this.file = file;
        this.factory = factory;
        this.name = file == null ? key : file.getFileName().toString();
    }

    public String key() {
        return key;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public Path file() {
        return file;
    }

    public String fileName() {
        return file == null ? null : file.getFileName().toString();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    public String error() {
        return error;
    }

    public List<Plugin> plugins() {
        return List.copyOf(plugins);
    }

    public State state() {
        if (error != null) {
            return State.FAILED;
        }
        return enabled && !plugins.isEmpty() ? State.ACTIVE : State.DISABLED;
    }

    public String pluginId() {
        if (builtin) {
            return "builtin." + key;
        }
        if (manifestId != null) {
            return manifestId;
        }
        String base = key;
        if (base.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            base = base.substring(0, base.length() - 4);
        }
        return base;
    }

    public int faults() {
        return faults;
    }

    public int slowCalls() {
        return slowCalls;
    }

    public String lastFault() {
        return lastFault;
    }

    public boolean matches(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        if (key.toLowerCase(Locale.ROOT).equals(wanted) || (manifestId != null && manifestId.toLowerCase(Locale.ROOT).equals(wanted))) {
            return true;
        }
        for (String className : classNames) {
            if (className.toLowerCase(Locale.ROOT).equals(wanted)) {
                return true;
            }
        }
        if (file != null) {
            String base = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (base.endsWith(".jar")) {
                base = base.substring(0, base.length() - 4);
            }
            return base.equals(wanted);
        }
        return false;
    }
}
