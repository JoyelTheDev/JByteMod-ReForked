package dev.joyel.hierarchy;

import me.grax.jbytemod.JarArchive;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

public enum HierarchyManager {
    INSTANCE;

    private volatile HierarchyIndex currentIndex;
    private final AtomicBoolean building = new AtomicBoolean(false);

    public static HierarchyManager getInstance() {
        return INSTANCE;
    }

    public void buildAsync(JarArchive archive, Runnable onComplete) {
        if (archive == null || archive.getClasses() == null) return;
        if (!building.compareAndSet(false, true)) return;

        new SwingWorker<HierarchyIndex, Void>() {
            @Override
            protected HierarchyIndex doInBackground() {
                return HierarchyIndex.build(archive.getClasses());
            }

            @Override
            protected void done() {
                try {
                    currentIndex = get();
                } catch (Exception ignored) {
                } finally {
                    building.set(false);
                    if (onComplete != null) {
                        SwingUtilities.invokeLater(onComplete);
                    }
                }
            }
        }.execute();
    }

    public void buildSync(JarArchive archive) {
        if (archive == null || archive.getClasses() == null) return;
        currentIndex = HierarchyIndex.build(archive.getClasses());
    }

    public void clear() {
        currentIndex = null;
    }

    public boolean isReady() {
        return currentIndex != null;
    }

    public boolean isBuilding() {
        return building.get();
    }

    public HierarchyIndex getIndex() {
        return currentIndex;
    }
}
