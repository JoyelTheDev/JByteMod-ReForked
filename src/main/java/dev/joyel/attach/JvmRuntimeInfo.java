package dev.joyel.attach;

public record JvmRuntimeInfo(
        String processName,
        String vmName,
        String vmVendor,
        String vmVersion,
        String runtimeName,
        String runtimeVersion,
        long startTime,
        long uptime,
        int processors,
        long heapUsed,
        long heapCommitted,
        long heapMax,
        long nonHeapUsed,
        long nonHeapCommitted,
        long nonHeapMax,
        int loadedClasses,
        long totalLoadedClasses,
        long unloadedClasses,
        int threadCount,
        int peakThreadCount,
        int daemonThreadCount,
        long totalStartedThreads,
        boolean frozen) {
}
