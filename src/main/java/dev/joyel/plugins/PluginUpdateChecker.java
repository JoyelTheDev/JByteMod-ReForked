package dev.joyel.plugins;

import java.util.ArrayList;
import java.util.List;

public final class PluginUpdateChecker {
    private PluginUpdateChecker() {
    }

    public record Update(PluginRepositoryService.RepositoryPlugin entry, PluginRecord installed) {
        public String marker() {
            return entry.id() + "@" + entry.version();
        }

        public String describe() {
            return entry.name() + " " + installed.version() + " -> " + entry.version();
        }
    }

    public static List<Update> findUpdates(PluginHost host, PluginRepositoryService.Catalog catalog, String applicationVersion) {
        List<Update> updates = new ArrayList<>();
        for (PluginRecord record : host.records()) {
            if (record.isBuiltin() || record.version().isEmpty()) {
                continue;
            }
            for (PluginRepositoryService.RepositoryPlugin entry : catalog.plugins()) {
                if (!record.matches(entry.id())) {
                    continue;
                }
                if (entry.downloadable() && entry.compatibleWith(applicationVersion)
                        && VersionCompare.isNewer(entry.version(), record.version())) {
                    updates.add(new Update(entry, record));
                }
                break;
            }
        }
        return updates;
    }

    public static List<Update> check(PluginHost host, PluginRepositoryService repository, String applicationVersion) {
        return findUpdates(host, repository.loadCatalog(), applicationVersion);
    }
}
