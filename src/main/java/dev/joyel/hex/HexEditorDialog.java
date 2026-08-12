package dev.joyel.hex;

import me.grax.jbytemod.JarArchive;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class HexEditorDialog extends JDialog {

    private final ResourceEntry entry;
    private final JarArchive jarArchive;
    private final HexEditorPanel hexPanel;
    private final JLabel statusLabel;
    private final JLabel sizeLabel;
    private final JLabel dirtyLabel;
    private boolean dirty = false;

    public HexEditorDialog(Frame parent, ResourceEntry entry, JarArchive jarArchive) {
        super(parent, "Hex Editor  —  " + entry.getPath(), false);
        this.entry      = entry;
        this.jarArchive = jarArchive;
        this.hexPanel   = new HexEditorPanel(entry.getData(), false);

        hexPanel.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                dirty = true;
                dirtyLabel.setText("● Modified");
                dirtyLabel.setForeground(new Color(0xff, 0x80, 0x00));
                updateStatus();
            }
        });

        statusLabel = new JLabel("Offset: 0x00000000   (0)");
        statusLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        sizeLabel   = new JLabel("Size: " + entry.size() + " bytes");
        sizeLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        dirtyLabel  = new JLabel("Clean");
        dirtyLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        dirtyLabel.setForeground(new Color(0x4e, 0xc9, 0xb0));

        setLayout(new BorderLayout());
        add(buildToolBar(), BorderLayout.NORTH);
        add(new JScrollPane(hexPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED),
                BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(860, 560));
        pack();
        setLocationRelativeTo(parent);
        hexPanel.requestFocusInWindow();
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(new EmptyBorder(2, 4, 2, 4));

        JButton saveBackBtn = new JButton("Save to JAR");
        saveBackBtn.setToolTipText("Write changes back into the loaded JAR in memory");
        saveBackBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { saveBack(); }
        });
        bar.add(saveBackBtn);

        JButton exportBtn = new JButton("Export File...");
        exportBtn.setToolTipText("Save raw bytes to a file on disk");
        exportBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { exportFile(); }
        });
        bar.add(exportBtn);

        JButton importBtn = new JButton("Import File...");
        importBtn.setToolTipText("Replace bytes with content of a file on disk");
        importBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { importFile(); }
        });
        bar.add(importBtn);

        bar.addSeparator();

        JButton gotoBtn = new JButton("Go to Offset...");
        gotoBtn.setMnemonic(KeyEvent.VK_G);
        gotoBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { gotoOffset(); }
        });
        bar.add(gotoBtn);

        JButton findBtn = new JButton("Find Hex...");
        findBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { findHex(); }
        });
        bar.add(findBtn);

        bar.addSeparator();

        JButton roBtn = new JButton("Toggle Read-Only");
        roBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                hexPanel.setReadOnly(!hexPanel.isReadOnly());
                roBtn.setText(hexPanel.isReadOnly() ? "Read-Only: ON" : "Read-Only: OFF");
            }
        });
        bar.add(roBtn);

        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 3));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));
        bar.add(statusLabel);
        bar.add(new JSeparator(SwingConstants.VERTICAL));
        bar.add(sizeLabel);
        bar.add(new JSeparator(SwingConstants.VERTICAL));
        bar.add(dirtyLabel);
        return bar;
    }

    private void updateStatus() {
        int offset = hexPanel.getCursorOffset();
        statusLabel.setText(String.format("Offset: 0x%08X   (%d)", offset, offset));
        sizeLabel.setText("Size: " + hexPanel.getData().length + " bytes");
    }

    private void saveBack() {
        byte[] current = hexPanel.getData();
        entry.setData(current);
        if (jarArchive != null && jarArchive.getOutput() != null) {
            jarArchive.getOutput().put(entry.getPath(), current);
        }
        dirty = false;
        dirtyLabel.setText("Saved");
        dirtyLabel.setForeground(new Color(0x4e, 0xc9, 0xb0));
        JOptionPane.showMessageDialog(this,
                "Changes saved to JAR in memory.\nSave the JAR from File > Save to write to disk.",
                "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exportFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Resource");
        fc.setSelectedFile(new File(entry.getDisplayName()));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File out = fc.getSelectedFile();
        try {
            FileOutputStream fos = new FileOutputStream(out);
            fos.write(hexPanel.getData());
            fos.close();
            JOptionPane.showMessageDialog(this, "Exported to:\n" + out.getAbsolutePath(),
                    "Export OK", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Import File to replace resource bytes");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File in = fc.getSelectedFile();
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(in);
            byte[] bytes = new byte[(int) in.length()];
            fis.read(bytes);
            fis.close();
            hexPanel.setData(bytes);
            dirty = true;
            dirtyLabel.setText("● Modified");
            dirtyLabel.setForeground(new Color(0xff, 0x80, 0x00));
            updateStatus();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(),
                    "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void gotoOffset() {
        String input = JOptionPane.showInputDialog(this,
                "Enter offset (decimal or 0x hex):", "Go to Offset", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) return;
        try {
            int offset = input.trim().startsWith("0x") || input.trim().startsWith("0X")
                    ? Integer.parseInt(input.trim().substring(2), 16)
                    : Integer.parseInt(input.trim());
            hexPanel.goTo(offset);
            updateStatus();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid offset: " + input,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int lastFindOffset = 0;

    private void findHex() {
        String input = JOptionPane.showInputDialog(this,
                "Enter hex bytes to find (e.g. CA FE BA BE):", "Find Hex", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) return;
        byte[] needle;
        try {
            needle = parseHexBytes(input.trim());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Invalid hex: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        byte[] haystack = hexPanel.getData();
        int start = (lastFindOffset + 1) % Math.max(1, haystack.length);
        int found = indexOf(haystack, needle, start);
        if (found < 0 && start > 0) found = indexOf(haystack, needle, 0);
        if (found < 0) {
            JOptionPane.showMessageDialog(this, "Pattern not found.", "Find", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        lastFindOffset = found;
        hexPanel.goTo(found);
        updateStatus();
    }

    private static byte[] parseHexBytes(String s) {
        String[] parts = s.split("\\s+");
        byte[] result = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() != 2) throw new IllegalArgumentException("Each byte must be 2 hex chars: " + parts[i]);
            result[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return result;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int start) {
        outer:
        for (int i = start; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
