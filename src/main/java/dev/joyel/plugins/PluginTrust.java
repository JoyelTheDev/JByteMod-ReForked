package dev.joyel.plugins;

public final class PluginTrust {
    private PluginTrust() {
    }

    public enum Decision {
        ALLOW_UNSIGNED,
        ALLOW_TRUSTED,
        PROMPT_NEW_PUBLISHER,
        BLOCK_INVALID,
        BLOCK_PUBLISHER_CHANGED,
        BLOCK_UNSIGNED_UPDATE;

        public boolean blocked() {
            return this == BLOCK_INVALID || this == BLOCK_PUBLISHER_CHANGED || this == BLOCK_UNSIGNED_UPDATE;
        }
    }

    public record Outcome(Decision decision, String fingerprint, String message) {
    }

    public static Outcome evaluate(PluginSettings settings, String pluginId, SignatureStatus status, String fingerprint) {
        String previous = settings.publisher(pluginId);
        if (status == SignatureStatus.INVALID) {
            return new Outcome(Decision.BLOCK_INVALID, fingerprint,
                    "The signature does not match this download, so the file may have been tampered with. Nothing was installed.");
        }
        if (status == SignatureStatus.UNSIGNED) {
            if (previous != null) {
                return new Outcome(Decision.BLOCK_UNSIGNED_UPDATE, previous,
                        "The version you have installed is signed by publisher " + previous
                                + ", but this update is not signed. Remove the plugin first if you really want to replace it.");
            }
            return new Outcome(Decision.ALLOW_UNSIGNED, null, "This plugin is not signed.");
        }
        if (previous != null && !previous.equals(fingerprint)) {
            return new Outcome(Decision.BLOCK_PUBLISHER_CHANGED, fingerprint,
                    "The installed version is signed by publisher " + previous + ", but this update is signed by "
                            + fingerprint + ". Remove the plugin first if you trust the new publisher.");
        }
        if (settings.isTrustedKey(fingerprint)) {
            return new Outcome(Decision.ALLOW_TRUSTED, fingerprint, "Signed by trusted publisher " + fingerprint + ".");
        }
        return new Outcome(Decision.PROMPT_NEW_PUBLISHER, fingerprint,
                "Signed by publisher " + fingerprint + ", which you have not trusted before.");
    }

    public static void recordInstall(PluginSettings settings, String pluginId, String fingerprint, boolean trustPublisher) {
        if (fingerprint == null) {
            return;
        }
        settings.setPublisher(pluginId, fingerprint);
        if (trustPublisher) {
            settings.trustKey(fingerprint);
        }
    }
}
