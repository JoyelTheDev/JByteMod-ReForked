package dev.joyel.attach;

public record CaptureStatus(
        boolean recording,
        int count,
        long totalBytes,
        long maxBytes,
        long dropped,
        boolean overflow,
        long sequence,
        boolean onlyNoCodeSource,
        boolean includeLambdas) {
}
