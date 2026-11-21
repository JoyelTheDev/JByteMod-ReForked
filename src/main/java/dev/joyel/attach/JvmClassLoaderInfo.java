package dev.joyel.attach;

public record JvmClassLoaderInfo(
        String id,
        String name,
        String type,
        String parentId,
        int classCount,
        boolean bootstrap) {
}
