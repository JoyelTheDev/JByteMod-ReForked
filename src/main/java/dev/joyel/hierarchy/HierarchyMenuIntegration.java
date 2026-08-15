package dev.joyel.hierarchy;

import de.xbrowniecodez.jbytemod.JByteMod;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public final class HierarchyMenuIntegration {

    private HierarchyMenuIntegration() {}

    public static JMenu createMenu(JByteMod jbm) {
        JMenu menu = new JMenu("Hierarchy");
        menu.setMnemonic(KeyEvent.VK_H);

        JMenuItem showItem = new JMenuItem("Show Method Hierarchy\u2026");
        showItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H,
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        showItem.setToolTipText("Show the class hierarchy and override group for the current method");
        showItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openForCurrentMethod(jbm);
            }
        });

        JMenuItem rebuildItem = new JMenuItem("Rebuild Hierarchy Index");
        rebuildItem.setToolTipText("Force-rebuild the hierarchy index from the loaded JAR");
        rebuildItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                rebuildIndex(jbm);
            }
        });

        menu.add(showItem);
        menu.addSeparator();
        menu.add(rebuildItem);
        return menu;
    }

    public static void installInto(JMenuBar menuBar, JByteMod jbm) {
        menuBar.add(createMenu(jbm));
        menuBar.revalidate();
        menuBar.repaint();
    }

    private static void openForCurrentMethod(JByteMod jbm) {
        ClassNode  cn = jbm.getCurrentNode();
        MethodNode mn = jbm.getCurrentMethod();
        if (cn == null) {
            JOptionPane.showMessageDialog(jbm,
                    "Please select a class or method first.",
                    "Hierarchy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        HierarchyViewerFrame.open(jbm, cn, mn);
    }

    private static void rebuildIndex(JByteMod jbm) {
        if (jbm.getJarArchive() == null) {
            JOptionPane.showMessageDialog(jbm,
                    "No JAR loaded.",
                    "Hierarchy", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HierarchyManager.getInstance().clear();
        HierarchyManager.getInstance().buildAsync(jbm.getJarArchive(), new Runnable() {
            public void run() {
                JOptionPane.showMessageDialog(jbm,
                        "Hierarchy index rebuilt.",
                        "Hierarchy", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }
}
