package dev.joyel.nbridge;

import java.util.List;

public record NativeLibrary(
        String path,
        String format,
        String architecture,
        int bits,
        long size,
        List<NativeExport> exports,
        boolean jniOnLoad,
        boolean jniOnUnload,
        String error) {

    public String fileName() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public int jniExportCount() {
        int count = 0;
        for (NativeExport export : exports) {
            if (JniNames.normalizeExport(export.name(), this).startsWith("Java_")) {
                count++;
            }
        }
        return count;
    }
}
