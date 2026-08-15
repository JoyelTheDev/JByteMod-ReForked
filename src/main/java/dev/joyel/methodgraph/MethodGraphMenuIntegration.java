package dev.joyel.methodgraph;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.event.KeyEvent;

public final class MethodGraphMenuIntegration {

    private MethodGraphMenuIntegration() {}

    public static JMenu createMenu(JByteMod jbm) {
        JMenu menu = new JMenu("Graph");
        menu.setMnemonic(KeyEvent.VK_G);

        JMenuItem openItem = new JMenuItem("Method Call Graph…");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G,
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        openItem.setToolTipText("Open the method call graph for the currently selected method");
        openItem.addActionListener(e -> openForCurrentMethod(jbm));

        menu.add(openItem);
        return menu;
    }

    public static void installInto(JMenuBar menuBar, JByteMod jbm) {
        menuBar.add(createMenu(jbm));
        menuBar.revalidate();
        menuBar.repaint();
    }

    private static void openForCurrentMethod(JByteMod jbm) {
        MethodNode mn = jbm.getCurrentMethod();
        ClassNode  cn = jbm.getCurrentNode();
        if (mn == null || cn == null) {
            JOptionPane.showMessageDialog(jbm,
                    "Please select a method first.",
                    "Method Graph", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        MethodGraphFrame.open(jbm, cn.name, mn);
    }
}
