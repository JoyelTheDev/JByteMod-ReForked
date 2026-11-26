package dev.joyel.plugins;

import de.xbrowniecodez.jbytemod.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PluginHost implements AutoCloseable {
    public static final String ATTRIBUTE_CLASS = "JByteMod-Plugin-Class";
    public static final String ATTRIBUTE_ID = "JByteMod-Plugin-Id";
    public static final String ATTRIBUTE_DESCRIPTION = "JByteMod-Plugin-Description";
    private static final long MAX_JAR_SIZE = 100L * 1024 * 1024;
    private static final int MAX_ENTRIES = 200_000;

    public interface Listener {
        void pluginsChanged(List<Plugin> newlyActivated);
    }

    public record InspectResult(List<String> pluginClasses, String manifestId, String description) {
    }

    private record JarMeta(List<String> classNames, boolean explicit, String manifestId, String description) {
    }

    private final Path folder;
    private final PluginSettings settings;
    private final Consumer<String> logger;
    private final List<PluginRecord> records = new ArrayList<>();
    private Listener listener;

    public PluginHost(Path folder, PluginSettings settings, Consumer<String> logger) {
        this.folder = folder;
        this.settings = settings;
        this.logger = logger;
    }

    public Path folder() {
        return folder;
    }

    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void registerBuiltin(String key, Supplier<Plugin> factory) {
        PluginRecord record = new PluginRecord(key, true, null, factory);
        record.enabled = settings.isEnabled("builtin." + key);
        records.removeIf(existing -> existing.builtin && existing.key.equals(key));
        records.add(0, record);
        if (record.enabled) {
            startBuiltin(record);
        } else {
            fillFromCache(record, "builtin." + key);
        }
    }

    public synchronized void loadAll() {
        List<Plugin> started = new ArrayList<>();
        for (Path file : listJarFiles()) {
            PluginRecord record = recordFor(file);
            record.enabled = settings.isEnabled(record.key);
            if (record.enabled) {
                started.addAll(startJar(record));
            } else {
                fillFromCache(record, record.key);
            }
        }
        sortRecords();
        log(countActive() + " plugin(s) active");
        notifyListener(started);
    }

    public synchronized void reload() {
        List<Plugin> started = new ArrayList<>();
        for (PluginRecord record : new ArrayList<>(records)) {
            if (!record.builtin) {
                stop(record);
            }
        }
        records.removeIf(record -> !record.builtin);
        for (Path file : listJarFiles()) {
            PluginRecord record = recordFor(file);
            record.enabled = settings.isEnabled(record.key);
            if (record.enabled) {
                started.addAll(startJar(record));
            } else {
                fillFromCache(record, record.key);
            }
        }
        sortRecords();
        notifyListener(started);
    }

    public synchronized List<PluginRecord> records() {
        return List.copyOf(records);
    }

    public synchronized ArrayList<Plugin> activePlugins() {
        ArrayList<Plugin> result = new ArrayList<>();
        for (PluginRecord record : records) {
            if (record.enabled && record.error == null) {
                result.addAll(record.plugins);
            }
        }
        return result;
    }

    public synchronized void setEnabled(PluginRecord record, boolean enabled) {
        if (record.enabled == enabled) {
            return;
        }
        String settingsKey = record.builtin ? "builtin." + record.key : record.key;
        record.enabled = enabled;
        settings.setEnabled(settingsKey, enabled);
        List<Plugin> started = List.of();
        if (enabled) {
            started = record.builtin ? startBuiltin(record) : startJar(record);
        } else {
            stop(record);
            record.error = null;
            fillFromCache(record, settingsKey);
        }
        notifyListener(started);
    }

    public synchronized InspectResult inspect(Path jar) throws IOException {
        if (Files.size(jar) > MAX_JAR_SIZE) {
            throw new IOException("The file is larger than the 100 MB limit");
        }
        JarMeta meta = readMeta(jar);
        if (meta.classNames().isEmpty()) {
            throw new IOException("This file contains no classes, so it is not a plugin");
        }
        List<String> pluginClasses = new ArrayList<>();
        try (URLClassLoader probe = new URLClassLoader(new URL[]{jar.toUri().toURL()}, Plugin.class.getClassLoader())) {
            for (String className : meta.classNames()) {
                try {
                    Class<?> type = Class.forName(className, false, probe);
                    if (isInstantiablePlugin(type)) {
                        pluginClasses.add(className);
                    }
                } catch (ClassNotFoundException | LinkageError ignored) {
                }
            }
        }
        if (pluginClasses.isEmpty()) {
            throw new IOException("This JAR does not contain a JByteMod plugin (no class extends Plugin)");
        }
        return new InspectResult(List.copyOf(pluginClasses), meta.manifestId(), meta.description());
    }

    public synchronized PluginRecord install(Path source, String requestedFileName, String pluginId) throws IOException {
        InspectResult info = inspect(source);
        PluginRecord existing = findInstalled(pluginId, info);
        Path target;
        boolean keepEnabled = true;
        if (existing != null) {
            target = existing.file;
            keepEnabled = existing.enabled;
            stop(existing);
        } else {
            target = uniqueTarget(sanitizeFileName(requestedFileName != null ? requestedFileName : source.getFileName().toString()));
        }
        Files.createDirectories(folder);
        Path backup = null;
        if (Files.exists(target)) {
            backup = target.resolveSibling(target.getFileName() + ".backup");
            moveReplacing(target, backup);
        }
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            restoreBackup(target, backup);
            if (existing != null && keepEnabled) {
                startJar(existing);
            }
            throw failure;
        }
        PluginRecord record = existing != null ? existing : new PluginRecord(target.getFileName().toString(), false, target, null);
        record.classNames = info.pluginClasses();
        record.enabled = keepEnabled;
        if (existing == null) {
            records.add(record);
        }
        List<Plugin> started = List.of();
        if (keepEnabled) {
            started = startJar(record);
            if (record.error != null) {
                String reason = record.error;
                stop(record);
                if (backup != null) {
                    restoreBackup(target, backup);
                    record.error = null;
                    startJar(record);
                    notifyListener(List.of());
                    throw new IOException("The new version failed to load (" + reason + "). The previous version was restored.");
                }
                Files.deleteIfExists(target);
                records.remove(record);
                settings.forget(record.key);
                notifyListener(List.of());
                throw new IOException("The plugin failed to load: " + reason);
            }
        } else {
            fillFromCache(record, record.key);
        }
        if (backup != null) {
            Files.deleteIfExists(backup);
        }
        sortRecords();
        notifyListener(started);
        return record;
    }

    public synchronized void remove(PluginRecord record) throws IOException {
        if (record.builtin) {
            throw new IOException("Built-in plugins cannot be removed. Disable them instead.");
        }
        stop(record);
        Files.deleteIfExists(record.file);
        Files.deleteIfExists(record.file.resolveSibling(record.file.getFileName() + ".backup"));
        settings.forget(record.key);
        records.remove(record);
        log("Removed plugin " + record.key);
        notifyListener(List.of());
    }

    @Override
    public synchronized void close() {
        for (PluginRecord record : new ArrayList<>(records)) {
            stop(record);
        }
        records.clear();
    }

    private PluginRecord findInstalled(String pluginId, InspectResult info) {
        for (PluginRecord record : records) {
            if (record.builtin) {
                continue;
            }
            if (pluginId != null && record.matches(pluginId)) {
                return record;
            }
            if (info.manifestId() != null && record.matches(info.manifestId())) {
                return record;
            }
            for (String className : info.pluginClasses()) {
                if (record.classNames.contains(className)) {
                    return record;
                }
            }
        }
        return null;
    }

    private List<Plugin> startBuiltin(PluginRecord record) {
        List<Plugin> started = new ArrayList<>();
        try {
            Plugin plugin = record.factory.get();
            plugin.init();
            started.add(plugin);
            record.plugins.clear();
            record.plugins.addAll(started);
            record.error = null;
            applyMetadata(record, settings, "builtin." + record.key);
            log("Built-in plugin registered: " + plugin.getName() + " v" + plugin.getVersion());
        } catch (Exception | LinkageError failure) {
            record.plugins.clear();
            record.error = describe(failure);
            log("Failed to start built-in plugin " + record.key + ": " + record.error);
        }
        return started;
    }

    private List<Plugin> startJar(PluginRecord record) {
        record.error = null;
        record.plugins.clear();
        URLClassLoader loader = null;
        List<Plugin> started = new ArrayList<>();
        try {
            JarMeta meta = readMeta(record.file);
            record.manifestId = meta.manifestId();
            record.description = meta.description() == null ? "" : meta.description();
            record.classNames = meta.classNames();
            loader = new URLClassLoader(new URL[]{record.file.toUri().toURL()}, Plugin.class.getClassLoader());
            for (String className : meta.classNames()) {
                Class<?> type;
                try {
                    type = Class.forName(className, false, loader);
                } catch (ClassNotFoundException | LinkageError failure) {
                    if (meta.explicit()) {
                        throw new IllegalStateException("Cannot load " + className + ": " + describe(failure));
                    }
                    continue;
                }
                if (!isInstantiablePlugin(type)) {
                    if (meta.explicit()) {
                        throw new IllegalStateException(className + " does not extend Plugin");
                    }
                    continue;
                }
                Plugin instance = (Plugin) type.getDeclaredConstructor().newInstance();
                instance.init();
                started.add(instance);
            }
            if (started.isEmpty()) {
                throw new IllegalStateException("No plugin class found in " + record.file.getFileName());
            }
            record.loader = loader;
            record.plugins.addAll(started);
            applyMetadata(record, settings, record.key);
            log("Plugin loaded: " + record.name + " v" + record.version);
            return started;
        } catch (Exception | LinkageError failure) {
            for (Plugin plugin : started) {
                shutdownQuietly(plugin);
            }
            closeQuietly(loader);
            record.loader = null;
            record.plugins.clear();
            record.error = describe(failure);
            log("Plugin " + record.file.getFileName() + " failed to load: " + record.error);
            return List.of();
        }
    }

    private void applyMetadata(PluginRecord record, PluginSettings store, String settingsKey) {
        List<String> names = new ArrayList<>();
        for (Plugin plugin : record.plugins) {
            names.add(plugin.getName());
        }
        Plugin first = record.plugins.get(0);
        record.name = String.join(", ", names);
        record.version = first.getVersion() == null ? "" : first.getVersion();
        record.author = first.getAuthor() == null ? "" : first.getAuthor();
        store.cacheInfo(settingsKey, record.name, record.version, record.author, record.description);
    }

    private void fillFromCache(PluginRecord record, String settingsKey) {
        String[] cached = settings.cachedInfo(settingsKey);
        if (cached != null) {
            record.name = cached[0].isEmpty() ? record.name : cached[0];
            record.version = cached[1];
            record.author = cached[2];
            record.description = cached[3];
        }
    }

    private void stop(PluginRecord record) {
        List<Plugin> running = new ArrayList<>(record.plugins);
        Collections.reverse(running);
        for (Plugin plugin : running) {
            shutdownQuietly(plugin);
        }
        record.plugins.clear();
        closeQuietly(record.loader);
        record.loader = null;
    }

    private void shutdownQuietly(Plugin plugin) {
        try {
            plugin.shutdown();
        } catch (Throwable failure) {
            log("Plugin " + plugin.getName() + " failed to shut down cleanly: " + describe(failure));
        }
    }

    private static void closeQuietly(URLClassLoader loader) {
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException ignored) {
            }
        }
    }

    private PluginRecord recordFor(Path file) {
        String key = file.getFileName().toString();
        for (PluginRecord record : records) {
            if (!record.builtin && record.key.equals(key)) {
                return record;
            }
        }
        PluginRecord record = new PluginRecord(key, false, file, null);
        records.add(record);
        return record;
    }

    private void sortRecords() {
        records.sort(Comparator.comparing((PluginRecord record) -> !record.builtin)
                .thenComparing(record -> record.key.toLowerCase(Locale.ROOT)));
    }

    private int countActive() {
        int count = 0;
        for (PluginRecord record : records) {
            if (record.state() == PluginRecord.State.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    private List<Path> listJarFiles() {
        if (!Files.isDirectory(folder)) {
            try {
                Files.createDirectories(folder);
            } catch (IOException failure) {
                log("Could not create the plugin folder: " + failure.getMessage());
                return List.of();
            }
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException failure) {
            log("Could not read the plugin folder: " + failure.getMessage());
            return List.of();
        }
    }

    private JarMeta readMeta(Path jar) throws IOException {
        List<String> scanned = new ArrayList<>();
        List<String> explicit = new ArrayList<>();
        String manifestId = null;
        String description = null;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) {
                    throw new IOException("The archive has too many entries");
                }
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".class") && !name.startsWith("META-INF/")
                        && !name.equals("module-info.class") && !name.endsWith("package-info.class")) {
                    scanned.add(name.substring(0, name.length() - 6).replace('/', '.'));
                }
            }
            ZipEntry manifestEntry = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifestEntry != null) {
                try (InputStream stream = zip.getInputStream(manifestEntry)) {
                    Attributes attributes = new Manifest(stream).getMainAttributes();
                    String declared = attributes.getValue(ATTRIBUTE_CLASS);
                    if (declared != null) {
                        for (String className : declared.trim().split("[,\\s]+")) {
                            if (className.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
                                explicit.add(className);
                            }
                        }
                    }
                    manifestId = blankToNull(attributes.getValue(ATTRIBUTE_ID));
                    description = blankToNull(attributes.getValue(ATTRIBUTE_DESCRIPTION));
                }
            }
        }
        if (!explicit.isEmpty()) {
            return new JarMeta(List.copyOf(new LinkedHashSet<>(explicit)), true, manifestId, description);
        }
        return new JarMeta(List.copyOf(scanned), false, manifestId, description);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isInstantiablePlugin(Class<?> candidate) {
        return Plugin.class.isAssignableFrom(candidate)
                && candidate != Plugin.class
                && !candidate.isInterface()
                && !Modifier.isAbstract(candidate.getModifiers());
    }

    static String sanitizeFileName(String requested) {
        String name = Path.of(requested.replace('\\', '/')).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name += ".jar";
        }
        if (name.startsWith(".") || !name.matches("[A-Za-z0-9._+-]+")) {
            name = name.replaceAll("[^A-Za-z0-9._+-]", "_").replaceFirst("^\\.+", "");
        }
        return name.isEmpty() || name.equals(".jar") ? "plugin.jar" : name;
    }

    private Path uniqueTarget(String fileName) {
        Path target = folder.resolve(fileName);
        if (!Files.exists(target)) {
            return target;
        }
        String base = fileName.substring(0, fileName.length() - 4);
        for (int index = 2; index < 1000; index++) {
            Path candidate = folder.resolve(base + "-" + index + ".jar");
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return folder.resolve(base + "-" + System.nanoTime() + ".jar");
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restoreBackup(Path target, Path backup) {
        if (backup == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
            if (Files.exists(backup)) {
                moveReplacing(backup, target);
            }
        } catch (IOException ignored) {
        }
    }

    private void notifyListener(List<Plugin> started) {
        if (listener != null) {
            try {
                listener.pluginsChanged(List.copyOf(started));
            } catch (RuntimeException failure) {
                log("Plugin listener failed: " + describe(failure));
            }
        }
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure;
        if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            cause = invocation.getCause();
        }
        String message = cause.getMessage();
        String type = cause.getClass().getSimpleName();
        return message == null || message.isBlank() ? type : type.equals("IllegalStateException") ? message : type + ": " + message;
    }

    public Set<String> installedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (PluginRecord record : records()) {
            keys.add(record.key);
        }
        return keys;
    }
}
