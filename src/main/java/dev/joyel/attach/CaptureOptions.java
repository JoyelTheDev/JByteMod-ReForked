package dev.joyel.attach;

public record CaptureOptions(boolean onlyNoCodeSource, boolean includeLambdas, int maxMegabytes) {
    public static CaptureOptions defaults() {
        return new CaptureOptions(false, false, 256);
    }
}
