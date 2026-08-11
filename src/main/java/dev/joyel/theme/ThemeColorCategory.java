package dev.joyel.theme;

public enum ThemeColorCategory {
    EDITOR("Code Editor"),
    SYNTAX("Syntax Colors"),
    UI("UI / Frame"),
    FILE_KIND("File Kind"),
    XREF_KIND("Xref Kind");

    private final String displayName;

    ThemeColorCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
