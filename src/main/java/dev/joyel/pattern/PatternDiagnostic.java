package dev.joyel.pattern;

public record PatternDiagnostic(int line, int column, Severity severity, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }
}
