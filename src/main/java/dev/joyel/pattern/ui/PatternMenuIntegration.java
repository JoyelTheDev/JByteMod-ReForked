package dev.joyel.pattern.ui;

import de.xbrowniecodez.jbytemod.JByteMod;

import javax.swing.*;
import java.awt.event.KeyEvent;

public final class PatternMenuIntegration {
    private PatternMenuIntegration() {
    }

    public static JMenu createMenu(JByteMod jbm) {
        JMenu menu = new JMenu("Pattern");
        menu.setMnemonic(KeyEvent.VK_P);

        JMenuItem searchItem = new JMenuItem("Pattern Search...");
        searchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        searchItem.addActionListener(e -> {
            PatternSearchDialog dlg = new PatternSearchDialog(jbm);
            dlg.setVisible(true);
        });

        JMenuItem replaceItem = new JMenuItem("Pattern Search & Replace...");
        replaceItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK));
        replaceItem.addActionListener(e -> {
            PatternReplaceDialog dlg = new PatternReplaceDialog(jbm);
            dlg.setVisible(true);
        });

        JMenuItem helpItem = new JMenuItem("Pattern Syntax Help");
        helpItem.addActionListener(e -> showHelp(jbm));

        menu.add(searchItem);
        menu.add(replaceItem);
        menu.addSeparator();
        menu.add(helpItem);
        return menu;
    }

    public static void installInto(JMenuBar menuBar, JByteMod jbm) {
        menuBar.add(createMenu(jbm));
        menuBar.revalidate();
        menuBar.repaint();
    }

    private static void showHelp(JByteMod parent) {
        String help =
                "Pattern Syntax\n" +
                "==============\n\n" +
                "Write instructions in the same assembler format used by JByteMod.\n" +
                "Each line is one instruction to match.\n\n" +
                "Wildcards\n" +
                "---------\n" +
                "  *           Match any single instruction (on its own line)\n" +
                "  ...         Match any sequence of zero or more instructions (gap)\n" +
                "  *           As an operand: match any value for that operand\n\n" +
                "Operand glob in quoted strings\n" +
                "------------------------------\n" +
                "  \"java/*\"    Match any class starting with java/\n" +
                "  \"get*\"      Match any method name starting with get\n" +
                "  \"?\"         Match exactly one character\n\n" +
                "Examples\n" +
                "--------\n" +
                "  # Match any ldc followed by println\n" +
                "  ldc *\n" +
                "  invokevirtual \"java/io/PrintStream\" \"println\" * *\n\n" +
                "  # Match any arithmetic with a gap in the middle\n" +
                "  iload *\n" +
                "  ...\n" +
                "  iadd\n" +
                "  ireturn\n\n" +
                "  # Enable 'Include Metadata' to also match labels, frames, line numbers.\n\n" +
                "Keyboard shortcuts\n" +
                "------------------\n" +
                "  Ctrl+Shift+F  Open Pattern Search\n" +
                "  Ctrl+Shift+H  Open Search & Replace\n" +
                "  Ctrl+Enter    Run search from inside the pattern editor\n";
        JTextArea ta = new JTextArea(help);
        ta.setEditable(false);
        ta.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JOptionPane.showMessageDialog(parent, new JScrollPane(ta), "Pattern Syntax Help", JOptionPane.INFORMATION_MESSAGE);
    }
}
