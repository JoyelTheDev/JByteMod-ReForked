package dev.joyel.deobf.api;

public final class PassResult {
    public final DeobfPass pass;
    public final boolean success;
    public final long durationMs;
    public final String error;

    public PassResult(DeobfPass pass, boolean success, long durationMs, String error) {
        this.pass = pass;
        this.success = success;
        this.durationMs = durationMs;
        this.error = error;
    }

    public static PassResult ok(DeobfPass pass, long durationMs) {
        return new PassResult(pass, true, durationMs, null);
    }

    public static PassResult fail(DeobfPass pass, long durationMs, String error) {
        return new PassResult(pass, false, durationMs, error);
    }
}
