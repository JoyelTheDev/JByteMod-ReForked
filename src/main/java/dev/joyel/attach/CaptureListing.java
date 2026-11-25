package dev.joyel.attach;

import java.util.List;

public record CaptureListing(long sequence, List<CapturedClassInfo> entries) {
}
