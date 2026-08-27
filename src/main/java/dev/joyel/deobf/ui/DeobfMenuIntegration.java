package dev.joyel.deobf.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.deobf.ui.DeobfPipelineDialog;

import javax.swing.*;
import java.awt.event.KeyEvent;

public final class DeobfMenuIntegration {

    public static void installInto(JMenuBar menuBar, JByteMod jbm) {
        JMenu menu = new JMenu("Deobfuscation");

        JMenuItem pipelineItem = new JMenuItem("Execution Pipeline\u2026");
        pipelineItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        pipelineItem.addActionListener(e -> {
            if (jbm.getJarArchive() == null) {
                JOptionPane.showMessageDialog(jbm, "Load a JAR or class file first.", "Deobfuscation Pipeline",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            DeobfPipelineDialog.open(jbm);
        });
        menu.add(pipelineItem);

        menuBar.add(menu);
    }

    private DeobfMenuIntegration() {}
}
