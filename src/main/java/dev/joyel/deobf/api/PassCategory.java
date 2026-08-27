package dev.joyel.deobf.api;

public enum PassCategory {
    GENERIC("Generic"),
    CLEANUP("Cleanup"),
    ANALYSIS("Analysis"),
    TOOLS("Tools"),
    ZKM("Obfuscators \u2022 ZKM");

    public final String displayName;

    PassCategory(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() { return displayName; }
}
