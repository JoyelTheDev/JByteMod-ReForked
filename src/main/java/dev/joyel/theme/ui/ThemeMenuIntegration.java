package dev.joyel.theme.ui;

import dev.joyel.theme.BuiltInThemes;
import dev.joyel.theme.JByteTheme;
import dev.joyel.theme.ThemeManager;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;

public final class ThemeMenuIntegration {
    private ThemeMenuIntegration() {}

    public static JMenu createMenu(final JFrame parent) {
        JMenu menu = new JMenu("Themes");
        menu.setMnemonic(KeyEvent.VK_T);

        JMenuItem editorItem = new JMenuItem("Theme Editor...");
        editorItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        editorItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ThemeEditorDialog dlg = new ThemeEditorDialog(parent);
                dlg.setVisible(true);
            }
        });
        menu.add(editorItem);
        menu.add(new JSeparator());

        for (final JByteTheme t : ThemeManager.getInstance().getThemes()) {
            JMenuItem item = new JMenuItem(t.getName());
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ThemeManager.getInstance().applyTheme(t);
                }
            });
            menu.add(item);
        }

        return menu;
    }

    public static void installInto(JMenuBar menuBar, JFrame parent) {
        installInto(menuBar, parent, null);
    }

    public static void installInto(JMenuBar menuBar, JFrame parent, File themeDirectory) {
        if (themeDirectory != null) {
            ThemeManager.getInstance().setThemeDirectory(themeDirectory);
        }
        menuBar.add(createMenu(parent));
        menuBar.revalidate();
        menuBar.repaint();
    }
}
