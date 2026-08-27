package dev.joyel.deobf.api;

public enum PassTag {
    POSSIBLE_DAMAGE("May make code unrunnable", "\u26A0"),
    RUNNABLE("Code stays runnable", "\u2713"),
    BETTER_DECOMPILE("Improves decompilability", "\ud83d\udcc4"),
    BETTER_DEOBFUSCATE("Helps other passes", "\ud83d\udd17"),
    SHRINK("Shrinks file size", "\u2193");

    public final String description;
    public final String icon;

    PassTag(String description, String icon) {
        this.description = description;
        this.icon = icon;
    }
}
