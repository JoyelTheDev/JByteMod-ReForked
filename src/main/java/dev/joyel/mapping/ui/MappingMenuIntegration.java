package dev.joyel.mapping.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.mapping.AggregateMappingManager;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public final class MappingMenuIntegration {

    private MappingMenuIntegration() {}

    public static void installInto(JMenuBar menuBar, JByteMod jbm) {
        AggregateMappingManager manager = new AggregateMappingManager();
        menuBar.add(createMenu(jbm, manager));
        menuBar.revalidate();
        menuBar.repaint();
    }

    public static JMenu createMenu(JByteMod jbm, AggregateMappingManager manager) {
        JMenu menu = new JMenu("Mapping");
        menu.setMnemonic(KeyEvent.VK_M);

        JMenuItem openItem = new JMenuItem("Open Mapping Manager...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        openItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MappingDialog dlg = new MappingDialog(jbm, manager);
                dlg.setVisible(true);
            }
        });

        menu.add(openItem);
        return menu;
    }
}
