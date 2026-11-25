package dev.joyel.attach;

import java.util.List;

public record CapturedClassInfo(
        long id,
        String name,
        String loaderId,
        String loaderName,
        String loaderType,
        String origin,
        long time,
        int size,
        String source,
        String thread,
        List<String> site,
        int seen,
        String sha256) {
}
