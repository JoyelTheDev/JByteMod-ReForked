package dev.joyel.pattern;

public final class PatternDiagnostic {
    public enum Severity {
        ERROR,
        WARNING
    }

    private final int line;
    private final int column;
    private final Severity severity;
    private final String message;

    public PatternDiagnostic(int line, int column, Severity severity, String message) {
        this.line = line;
        this.column = column;
        this.severity = severity;
        this.message = message;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public Severity severity() {
        return severity;
    }

    public String message() {
        return message;
    }
}