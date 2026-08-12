package me.grax.jbytemod.xref;

import me.grax.jbytemod.JarArchive;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public enum XrefManager {
    INSTANCE;

    private volatile XrefMap currentMap;
    private final AtomicBoolean building = new AtomicBoolean(false);

    public static XrefManager getInstance() {
        return INSTANCE;
    }

    public void buildAsync(JarArchive archive, Runnable onComplete) {
        if (archive == null || archive.getClasses() == null) return;
        if (!building.compareAndSet(false, true)) return;

        new SwingWorker<XrefMap, Void>() {
            @Override
            protected XrefMap doInBackground() {
                return new XrefMap(archive.getClasses());
            }

            @Override
            protected void done() {
                try {
                    currentMap = get();
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
        currentMap = new XrefMap(archive.getClasses());
    }

    public void clear() {
        currentMap = null;
    }

    public boolean isReady() {
        return currentMap != null;
    }

    public boolean isBuilding() {
        return building.get();
    }

    public List<XrefEntry> getMemberRefs(String owner, String name, String desc) {
        if (currentMap == null) return Collections.emptyList();
        return currentMap.getMemberRefs(owner, name, desc);
    }

    public List<XrefEntry> getClassRefs(String internalName) {
        if (currentMap == null) return Collections.emptyList();
        return currentMap.getClassRefs(internalName);
    }

    public XrefMap getCurrentMap() {
        return currentMap;
    }
}
