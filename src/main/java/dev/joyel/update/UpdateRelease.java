package dev.joyel.update;

public final class UpdateRelease {
    private final String version;
    private final String url;
    private final String changelog;

    public UpdateRelease(String version, String url, String changelog) {
        this.version = version;
        this.url = url;
        this.changelog = changelog != null ? changelog : "";
    }

    public String getVersion() {
        return version;
    }
    public String getUrl() {
        return url;
    }
    public String getChangelog() {
        return changelog;
    }
}