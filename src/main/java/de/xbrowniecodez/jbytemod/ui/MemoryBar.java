package de.xbrowniecodez.jbytemod.ui;

import dev.joyel.ui.ToastManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MemoryBar extends JPanel {
    private static final Color WARN_COLOR = new Color(0xF59E0B);
    private static final Color CRITICAL_COLOR = new Color(0xEF4444);

    private final JProgressBar progressBar;
    private final Color defaultColor;

    public MemoryBar() {
        setLayout(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        defaultColor = progressBar.getForeground();
        progressBar.setToolTipText("Click to run garbage collection");
        progressBar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        progressBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                collectGarbage();
            }
        });
        updateMemoryUsage();

        Timer timer = new Timer(1000, e -> {
            if (isShowing()) {
                updateMemoryUsage();
            }
        });
        timer.start();

        add(progressBar, BorderLayout.CENTER);
    }

    private void collectGarbage() {
        long before = usedMemory();
        System.gc();
        updateMemoryUsage();
        long freed = Math.max(0, before - usedMemory());
        ToastManager.info("Garbage collection freed " + formatMemorySize(freed));
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private void updateMemoryUsage() {
        long max = Runtime.getRuntime().maxMemory();
        long used = usedMemory();
        int percent = max <= 0 ? 0 : (int) Math.min(100, Math.round(used * 100.0 / max));

        progressBar.setValue(percent);
        progressBar.setString("Memory: " + formatMemorySize(used) + " / " + formatMemorySize(max) + " (" + percent + "%)");
        progressBar.setForeground(percent >= 90 ? CRITICAL_COLOR : percent >= 75 ? WARN_COLOR : defaultColor);
    }

    private static String formatMemorySize(long bytes) {
        long kilobytes = bytes / 1024;
        long megabytes = kilobytes / 1024;
        if (megabytes >= 1024) {
            return String.format("%.1f GB", megabytes / 1024.0);
        } else if (megabytes > 0) {
            return megabytes + " MB";
        }
        return kilobytes + " KB";
    }
}
