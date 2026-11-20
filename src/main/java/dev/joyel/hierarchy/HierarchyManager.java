package dev.joyel.hierarchy;

import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public enum HierarchyManager {
    INSTANCE;

    private volatile HierarchyIndex currentIndex;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicInteger running = new AtomicInteger();

    public static HierarchyManager getInstance() {
        return INSTANCE;
    }

    public void buildAsync(JarArchive archive, Runnable onComplete) {
        if (archive == null || archive.getClasses() == null) return;
        final long ticket = generation.incrementAndGet();
        final Map<String, ClassNode> classes = archive.getClasses();
        running.incrementAndGet();

        new SwingWorker<HierarchyIndex, Void>() {
            @Override
            protected HierarchyIndex doInBackground() {
                return HierarchyIndex.build(classes);
            }

            @Override
            protected void done() {
                try {
                    HierarchyIndex built = get();
                    if (ticket == generation.get()) {
                        currentIndex = built;
                    }
                } catch (Exception ignored) {
                } finally {
                    running.decrementAndGet();
                    if (onComplete != null) {
                        SwingUtilities.invokeLater(onComplete);
                    }
                }
            }
        }.execute();
    }

    public void buildSync(JarArchive archive) {
        if (archive == null || archive.getClasses() == null) return;
        generation.incrementAndGet();
        currentIndex = HierarchyIndex.build(archive.getClasses());
    }

    public void clear() {
        generation.incrementAndGet();
        currentIndex = null;
    }

    public boolean isReady() {
        return currentIndex != null;
    }

    public boolean isBuilding() {
        return running.get() > 0;
    }

    public HierarchyIndex getIndex() {
        return currentIndex;
    }
}
