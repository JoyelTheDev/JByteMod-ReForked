package dev.joyel.nbridge;

public enum MatchStatus {
    EXPORTED("Exported"),
    DYNAMIC("Maybe RegisterNatives"),
    NOT_FOUND("Not found"),
    NO_LIBRARY("No library");

    private final String label;

    MatchStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
