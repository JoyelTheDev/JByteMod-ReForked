package dev.joyel.nbridge;

import java.util.List;

public record NativeScan(
        List<NativeMethodInfo> methods,
        List<NativeLibrary> libraries,
        List<OrphanExport> orphans,
        List<LoadCall> loadCalls) {

    public int exportedCount() {
        int count = 0;
        for (NativeMethodInfo method : methods) {
            if (method.status() == MatchStatus.EXPORTED) {
                count++;
            }
        }
        return count;
    }
}
