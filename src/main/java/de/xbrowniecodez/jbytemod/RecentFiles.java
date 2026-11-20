package de.xbrowniecodez.jbytemod;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecentFiles {
    private static final int MAX_ENTRIES = 10;
    private static final RecentFiles INSTANCE = new RecentFiles();

    private final File storage = new File(System.getProperty("user.home"), ".jbytemod" + File.separator + "recent.prefs");
    private final List<File> files = new ArrayList<>();

    private RecentFiles() {
        load();
    }

    public static RecentFiles getInstance() {
        return INSTANCE;
    }

    public synchronized void add(File file) {
        if (file == null) {
            return;
        }
        File absolute = file.getAbsoluteFile();
        files.remove(absolute);
        files.add(0, absolute);
        while (files.size() > MAX_ENTRIES) {
            files.remove(files.size() - 1);
        }
        save();
    }

    public synchronized void remove(File file) {
        if (file != null && files.remove(file.getAbsoluteFile())) {
            save();
        }
    }

    public synchronized void clear() {
        files.clear();
        save();
    }

    public synchronized List<File> getFiles() {
        return Collections.unmodifiableList(new ArrayList<>(files));
    }

    private void load() {
        if (!storage.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(storage.toPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && files.size() < MAX_ENTRIES) {
                    files.add(new File(trimmed));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void save() {
        try {
            File parent = storage.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            List<String> lines = new ArrayList<>();
            for (File f : files) {
                lines.add(f.getPath());
            }
            Files.write(storage.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
