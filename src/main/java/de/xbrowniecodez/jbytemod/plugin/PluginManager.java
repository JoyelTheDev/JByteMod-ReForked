package de.xbrowniecodez.jbytemod.plugin;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.diff.DiffPlugin;
import de.xbrowniecodez.jbytemod.utils.Utils;
import lombok.Getter;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Getter
public class PluginManager implements AutoCloseable {

    private final ArrayList<Plugin> plugins = new ArrayList<>();
    private final File pluginFolder = new File(Utils.getWorkingDirectory(), "plugins");
    private URLClassLoader pluginClassLoader;

    public PluginManager(JByteMod jbm) {
        registerBuiltinPlugins();
        if (pluginFolder.exists() && pluginFolder.isDirectory()) {
            loadPlugins();
        } else {
            Main.INSTANCE.getLogger().err("No plugin folder found!");
        }
    }

    private void registerBuiltinPlugins() {
        registerBuiltin(new DiffPlugin());
    }

    private void registerBuiltin(Plugin plugin) {
        try {
            plugin.init();
            plugins.add(plugin);
            Main.INSTANCE.getLogger().log("Built-in plugin registered: " + plugin.getName() + " v" + plugin.getVersion());
        } catch (Exception e) {
            Main.INSTANCE.getLogger().err("Failed to register built-in plugin: " + plugin.getName());
            e.printStackTrace();
        }
    }

    private void loadPlugins() {
        File[] files = pluginFolder.listFiles();
        if (files == null) {
            Main.INSTANCE.getLogger().err("Plugin folder is empty or does not exist!");
            return;
        }

        ArrayList<URL> urls = new ArrayList<>();
        for (File file : files) {
            if (file.getName().endsWith(".jar")) {
                try {
                    urls.add(file.toURI().toURL());
                } catch (Exception e) {
                    Main.INSTANCE.getLogger().err("Failed to resolve URL for plugin: " + file.getName());
                }
            }
        }

        if (urls.isEmpty()) {
            Main.INSTANCE.getLogger().log("No plugin jars found.");
            return;
        }

        pluginClassLoader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());

        for (File file : files) {
            if (!file.getName().endsWith(".jar")) continue;
            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        loadClassFromEntry(name);
                    }
                }
            } catch (Exception e) {
                Main.INSTANCE.getLogger().err("Plugin " + file.getName() + " failed to load!");
                e.printStackTrace();
            }
        }
        Main.INSTANCE.getLogger().log(plugins.size() + " plugin(s) loaded!");
    }

    private void loadClassFromEntry(String name) {
        try {
            String className = name.replace('/', '.').substring(0, name.length() - 6);
            Class<?> loadedClass = Class.forName(className, true, pluginClassLoader);
            if (Plugin.class.isAssignableFrom(loadedClass) && !loadedClass.equals(Plugin.class)) {
                Plugin pluginInstance = (Plugin) loadedClass.getDeclaredConstructor().newInstance();
                pluginInstance.init();
                plugins.add(pluginInstance);
            }
        } catch (Exception e) {
            Main.INSTANCE.getLogger().err("Failed to load class " + name);
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        for (Plugin plugin : plugins) {
            try {
                plugin.shutdown();
            } catch (Exception ignored) {}
        }
        if (pluginClassLoader != null) {
            try {
                pluginClassLoader.close();
            } catch (Exception ignored) {}
        }
    }
}
