package dev.joyel.constpool;

import de.xbrowniecodez.jbytemod.JByteMod;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public final class ConstantPoolMenuIntegration {

    private ConstantPoolMenuIntegration() {}

    public static JMenu createMenu(JByteMod jbm) {
        JMenu menu = new JMenu("Constants");
        menu.setMnemonic(KeyEvent.VK_K);

        JMenuItem viewerItem = new JMenuItem("Constant Pool Viewer\u2026");
        viewerItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K,
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        viewerItem.setToolTipText("Browse and search all string, number and type constants in the loaded JAR");
        viewerItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jbm.getJarArchive() == null) {
                    JOptionPane.showMessageDialog(jbm, "No JAR loaded.",
                            "Constant Pool", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                ConstantPoolFrame.open(jbm);
            }
        });

        JMenuItem searchItem = new JMenuItem("Search Constants\u2026");
        searchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK));
        searchItem.setToolTipText("Open the constant pool viewer with a pre-filled search term");
        searchItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jbm.getJarArchive() == null) {
                    JOptionPane.showMessageDialog(jbm, "No JAR loaded.",
                            "Constant Pool", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                String term = JOptionPane.showInputDialog(jbm, "Search term:", "Search Constants",
                        JOptionPane.PLAIN_MESSAGE);
                if (term != null) {
                    ConstantPoolFrame.openWithQuery(jbm, term);
                }
            }
        });

        menu.add(viewerItem);
        menu.add(searchItem);
        return menu;
    }

    public static void installInto(JMenuBar menuBar, JByteMod jbm) {
        menuBar.add(createMenu(jbm));
        menuBar.revalidate();
        menuBar.repaint();
    }
}
