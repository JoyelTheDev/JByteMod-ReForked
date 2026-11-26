package de.xbrowniecodez.jbytemod.plugin;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.diff.DiffPlugin;
import de.xbrowniecodez.jbytemod.utils.Utils;
import dev.joyel.plugins.PluginHost;
import dev.joyel.plugins.PluginRepositoryService;
import dev.joyel.plugins.PluginSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PluginManager implements AutoCloseable {
    private final PluginHost host;
    private final PluginRepositoryService repository;

    public PluginManager(JByteMod jbm) {
        PluginSettings settings = PluginSettings.system();
        Path workingDirectory = Path.of(String.valueOf(Utils.getWorkingDirectory()));
        host = new PluginHost(workingDirectory.resolve("plugins"), settings, message -> Main.INSTANCE.getLogger().log(message));
        host.registerBuiltin("diff", DiffPlugin::new);
        host.loadAll();
        Path repositoryFolder = workingDirectory.resolve("plugin-repository");
        repository = new PluginRepositoryService(settings, repositoryFolder.resolve("cache"),
                repositoryFolder.resolve("downloads"), defaultRepositories());
    }

    private static List<String> defaultRepositories() {
        String configured = Utils.readPropertiesFile().getProperty("plugin.repository", "");
        List<String> result = new ArrayList<>();
        for (String entry : configured.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("${")) {
                result.add(trimmed);
            }
        }
        return result;
    }

    public ArrayList<Plugin> getPlugins() {
        return host.activePlugins();
    }

    public PluginHost getHost() {
        return host;
    }

    public PluginRepositoryService getRepository() {
        return repository;
    }

    @Override
    public void close() {
        host.close();
    }
}
