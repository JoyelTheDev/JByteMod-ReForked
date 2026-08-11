package dev.joyel.theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();

    private final List<JByteTheme> themes = new ArrayList<JByteTheme>();
    private JByteTheme activeTheme;
    private final List<ThemeChangeListener> listeners = new ArrayList<ThemeChangeListener>();
    private File themeDir;

    private ThemeManager() {
        themes.add(BuiltInThemes.dark());
        themes.add(BuiltInThemes.light());
        themes.add(BuiltInThemes.monokai());
        themes.add(BuiltInThemes.solarizedDark());
        activeTheme = themes.get(0);
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public void setThemeDirectory(File dir) {
        this.themeDir = dir;
        if (dir != null) dir.mkdirs();
        loadUserThemes();
    }

    public void addListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    public List<JByteTheme> getThemes() {
        return Collections.unmodifiableList(themes);
    }

    public JByteTheme getActiveTheme() {
        return activeTheme;
    }

    public void applyTheme(JByteTheme theme) {
        if (!themes.contains(theme)) return;
        activeTheme = theme;
        theme.applyToPalette();
        propagateToSwing(theme);
        for (ThemeChangeListener l : listeners) l.onThemeChanged(theme);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                for (java.awt.Window w : java.awt.Window.getWindows()) {
                    SwingUtilities.updateComponentTreeUI(w);
                    w.repaint();
                }
            }
        });
    }

    private void propagateToSwing(JByteTheme theme) {
        Color bg = theme.getColor("editor.background");
        Color fg = theme.getColor("editor.foreground");
        Color sel = theme.getColor("editor.selection");
        Color border = theme.getColor("editor.border");

        UIManager.put("Panel.background", bg);
        UIManager.put("Panel.foreground", fg);
        UIManager.put("Label.foreground", fg);
        UIManager.put("List.background", bg);
        UIManager.put("List.foreground", fg);
        UIManager.put("List.selectionBackground", sel);
        UIManager.put("List.selectionForeground", fg);
        UIManager.put("Tree.background", bg);
        UIManager.put("Tree.foreground", fg);
        UIManager.put("Tree.selectionBackground", sel);
        UIManager.put("Tree.selectionForeground", fg);
        UIManager.put("TextArea.background", bg);
        UIManager.put("TextArea.foreground", fg);
        UIManager.put("TextArea.selectionColor", sel);
        UIManager.put("TextArea.selectedTextColor", fg);
        UIManager.put("TextField.background", bg);
        UIManager.put("TextField.foreground", fg);
        UIManager.put("TextField.selectionColor", sel);
        UIManager.put("TextField.selectedTextColor", fg);
        UIManager.put("EditorPane.background", bg);
        UIManager.put("EditorPane.foreground", fg);
        UIManager.put("ScrollPane.background", bg);
        UIManager.put("Viewport.background", bg);
        UIManager.put("SplitPane.background", bg);
        UIManager.put("TabbedPane.background", bg);
        UIManager.put("TabbedPane.foreground", fg);
        UIManager.put("MenuBar.background", bg);
        UIManager.put("MenuBar.foreground", fg);
        UIManager.put("Menu.background", bg);
        UIManager.put("Menu.foreground", fg);
        UIManager.put("MenuItem.background", bg);
        UIManager.put("MenuItem.foreground", fg);
        UIManager.put("PopupMenu.background", bg);
        UIManager.put("PopupMenu.foreground", fg);
        UIManager.put("ToolBar.background", bg);
        UIManager.put("ToolBar.foreground", fg);
        UIManager.put("Separator.foreground", border);
        UIManager.put("Button.background", lighter(bg, 20));
        UIManager.put("Button.foreground", fg);
        UIManager.put("ToggleButton.background", lighter(bg, 20));
        UIManager.put("ToggleButton.foreground", fg);
        UIManager.put("ScrollBar.background", bg);
        UIManager.put("ScrollBar.thumb", lighter(bg, 40));
        UIManager.put("ScrollBar.thumbHighlight", lighter(bg, 55));
        UIManager.put("CheckBox.background", bg);
        UIManager.put("CheckBox.foreground", fg);
        UIManager.put("ComboBox.background", lighter(bg, 15));
        UIManager.put("ComboBox.foreground", fg);
        UIManager.put("Table.background", bg);
        UIManager.put("Table.foreground", fg);
        UIManager.put("Table.selectionBackground", sel);
        UIManager.put("Table.selectionForeground", fg);
        UIManager.put("TableHeader.background", lighter(bg, 10));
        UIManager.put("TableHeader.foreground", fg);
        UIManager.put("ToolTip.background", lighter(bg, 15));
        UIManager.put("ToolTip.foreground", fg);
        UIManager.put("OptionPane.background", bg);
        UIManager.put("OptionPane.messageForeground", fg);
        UIManager.put("TitledBorder.titleColor", fg);
    }

    private static Color lighter(Color c, int amount) {
        return new Color(
                Math.min(255, c.getRed() + amount),
                Math.min(255, c.getGreen() + amount),
                Math.min(255, c.getBlue() + amount));
    }

    public boolean addUserTheme(JByteTheme theme) {
        for (JByteTheme t : themes) {
            if (t.getName().equalsIgnoreCase(theme.getName())) return false;
        }
        themes.add(theme);
        return true;
    }

    public void removeUserTheme(JByteTheme theme) {
        if (theme.isBuiltIn()) return;
        themes.remove(theme);
        if (activeTheme == theme) applyTheme(themes.get(0));
        if (themeDir != null) {
            File f = themeFile(theme);
            if (f.exists()) f.delete();
        }
    }

    public void saveTheme(JByteTheme theme) {
        if (themeDir == null || theme.isBuiltIn()) return;
        File f = themeFile(theme);
        try {
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(theme.serialize().getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException e) {
            System.err.println("[ThemeManager] Failed to save theme: " + e.getMessage());
        }
    }

    private void loadUserThemes() {
        if (themeDir == null) return;
        File[] files = themeDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".jbtheme")) continue;
            try {
                FileInputStream fis = new FileInputStream(f);
                byte[] bytes = new byte[(int) f.length()];
                fis.read(bytes);
                fis.close();
                JByteTheme theme = JByteTheme.deserialize(new String(bytes, StandardCharsets.UTF_8));
                addUserTheme(theme);
            } catch (IOException e) {
                System.err.println("[ThemeManager] Failed to load theme file " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    private File themeFile(JByteTheme theme) {
        String safe = theme.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return new File(themeDir, safe + ".jbtheme");
    }

    public void saveActiveThemeName(File prefsFile) {
        try {
            FileOutputStream fos = new FileOutputStream(prefsFile);
            fos.write(("active_theme=" + activeTheme.getName()).getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException ignored) {}
    }

    public void loadActiveThemeByName(String name) {
        for (JByteTheme t : themes) {
            if (t.getName().equals(name)) { applyTheme(t); return; }
        }
    }
}
