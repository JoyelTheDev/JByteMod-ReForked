package dev.joyel.tutorial;

import de.xbrowniecodez.jbytemod.JByteMod;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

public final class TutorialMenuIntegration {

    private TutorialMenuIntegration() {}

    public static void installInto(JMenuBar menuBar, JByteMod jbm) {
        JMenu helpMenu = findOrCreateHelpMenu(menuBar);

        helpMenu.addSeparator();

        JMenuItem tutorialItem = new JMenuItem("Tutorial / Onboarding...");
        tutorialItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        tutorialItem.setToolTipText("Open the interactive step-by-step tutorial");
        tutorialItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TutorialDialog.open(jbm);
            }
        });
        helpMenu.add(tutorialItem);

        JMenuItem resetItem = new JMenuItem("Reset Tutorial State");
        resetItem.setToolTipText("Makes the tutorial appear again on next startup");
        resetItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                TutorialPrefs.reset();
                JOptionPane.showMessageDialog(jbm,
                    "Tutorial state reset.\nThe tutorial will appear again on next startup.",
                    "Tutorial Reset", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        helpMenu.add(resetItem);

        menuBar.revalidate();
        menuBar.repaint();
    }

    private static JMenu findOrCreateHelpMenu(JMenuBar menuBar) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && "Help".equalsIgnoreCase(menu.getText())) {
                return menu;
            }
        }
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        menuBar.add(helpMenu);
        return helpMenu;
    }
}
