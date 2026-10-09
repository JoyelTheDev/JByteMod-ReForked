package me.grax.jbytemod.ui;

import de.xbrowniecodez.jbytemod.ui.MemoryBar;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Year;

public class PageEndPanel extends JPanel {
    private static final String COPYRIGHT_TEXT = "© JByteMod 2020 - " + Year.now().getValue();
    private static final String DEFAULT_MESSAGE = "Ready";
    private static final int PROGRESS_WIDTH = 170;
    private static final int MEMORY_WIDTH = 210;

    private final JLabel messageLabel = new ShrinkableLabel();
    private final JLabel selectionLabel = new ShrinkableLabel();
    private final JLabel versionLabel = new JLabel();
    private final JLabel classCountLabel = new JLabel();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final MemoryBar memoryBar = new MemoryBar();

    private String task;

    public PageEndPanel() {
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(3, 6, 3, 6));
        setToolTipText(COPYRIGHT_TEXT);

        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        messageLabel.setFont(font);
        selectionLabel.setFont(font);
        versionLabel.setFont(font);
        classCountLabel.setFont(font);

        messageLabel.setText(DEFAULT_MESSAGE);
        selectionLabel.setText("No selection");
        selectionLabel.setEnabled(false);
        versionLabel.setText("");
        classCountLabel.setText("0 classes");

        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setPreferredSize(new Dimension(PROGRESS_WIDTH, 18));
        progressBar.setMinimumSize(new Dimension(PROGRESS_WIDTH, 18));

        memoryBar.setPreferredSize(new Dimension(MEMORY_WIDTH, 18));
        memoryBar.setMinimumSize(new Dimension(MEMORY_WIDTH, 18));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 4, 0, 4);

        c.weightx = 0.35;
        add(messageLabel, c);
        c.weightx = 0;
        add(separator(), c);
        c.weightx = 0.65;
        add(selectionLabel, c);
        c.weightx = 0;
        add(versionLabel, c);
        add(separator(), c);
        add(classCountLabel, c);
        add(separator(), c);
        add(progressBar, c);
        add(separator(), c);
        add(memoryBar, c);
    }

    private static JComponent separator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(2, 16));
        return separator;
    }

    private void onEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    public void setValue(int n) {
        onEdt(() -> {
            progressBar.setIndeterminate(false);
            if (n >= 100) {
                progressBar.setValue(0);
                progressBar.setString("");
                task = null;
            } else {
                progressBar.setValue(Math.max(0, n));
                progressBar.setString(task == null ? n + "%" : task + " " + n + "%");
            }
            progressBar.repaint();
        });
    }

    public void setTask(String name) {
        onEdt(() -> {
            task = name;
            if (name == null) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                progressBar.setString("");
            } else {
                progressBar.setString(name);
            }
        });
    }

    public void setBusy(String name) {
        onEdt(() -> {
            task = name;
            progressBar.setIndeterminate(true);
            progressBar.setString(name == null ? "" : name);
        });
    }

    public void setTip(String tooltipText) {
        onEdt(() -> {
            String text = tooltipText != null ? tooltipText : DEFAULT_MESSAGE;
            messageLabel.setText(text);
            messageLabel.setToolTipText(text);
        });
    }

    public void setClassCount(int count) {
        onEdt(() -> classCountLabel.setText(count + (count == 1 ? " class" : " classes")));
    }

    public void setSelection(ClassNode cn, MethodNode mn) {
        onEdt(() -> {
            if (cn == null) {
                selectionLabel.setText("No selection");
                selectionLabel.setToolTipText(null);
                selectionLabel.setEnabled(false);
                versionLabel.setText("");
                return;
            }
            selectionLabel.setEnabled(true);
            String text;
            if (mn == null) {
                text = cn.name;
            } else {
                int insns = mn.instructions == null ? 0 : mn.instructions.size();
                text = cn.name + "." + mn.name + mn.desc + "  ·  " + insns + " insns";
            }
            selectionLabel.setText(text);
            selectionLabel.setToolTipText(text);
            versionLabel.setText(" " + describeVersion(cn.version));
        });
    }

    public static String describeVersion(int version) {
        int major = version & 0xFFFF;
        if (major >= 49) {
            return "v" + major + " (Java " + (major - 44) + ")";
        }
        if (major >= 45) {
            return "v" + major + " (Java 1." + (major - 44) + ")";
        }
        return "v" + major;
    }

    private static final class ShrinkableLabel extends JLabel {
        @Override
        public Dimension getMinimumSize() {
            Dimension d = super.getMinimumSize();
            return new Dimension(0, d.height);
        }
    }
}
