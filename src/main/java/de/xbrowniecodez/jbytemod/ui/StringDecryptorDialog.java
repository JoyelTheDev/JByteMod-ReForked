package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import me.grax.jbytemod.utils.StringDecryptorUtils;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StringDecryptorDialog extends JDialog {

    private final JByteMod jbm;
    private final JTextField ownerField;
    private final JTextField nameField;
    private final JTextField descField;
    private final JTextArea logArea;
    private final JButton runButton;
    private final JButton scanButton;
    private final JList<String> candidateList;
    private final DefaultListModel<String> candidateModel;

    public StringDecryptorDialog(JByteMod jbm) {
        this.jbm = jbm;
        setTitle("String Decryptor");
        setModalityType(ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(200, 150, 680, 540);

        JPanel contentPane = new JPanel(new BorderLayout(6, 6));
        contentPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(contentPane);

        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        contentPane.add(topPanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 4, 4));
        inputPanel.setBorder(new TitledBorder("Decrypt Method"));

        inputPanel.add(new JLabel("Owner (e.g. com/example/Strings):"));
        ownerField = new JTextField();
        inputPanel.add(ownerField);

        inputPanel.add(new JLabel("Method Name:"));
        nameField = new JTextField();
        inputPanel.add(nameField);

        inputPanel.add(new JLabel("Descriptor (e.g. (Ljava/lang/String;I)Ljava/lang/String;):"));
        descField = new JTextField();
        inputPanel.add(descField);

        topPanel.add(inputPanel, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new GridLayout(1, 4, 6, 0));
        buttonRow.setBorder(new EmptyBorder(4, 0, 0, 0));

        scanButton = new JButton("Scan for Candidates");
        runButton = new JButton("Run Decryptor");
        JButton clearButton = new JButton("Clear Log");
        JButton closeButton = new JButton("Close");

        buttonRow.add(scanButton);
        buttonRow.add(runButton);
        buttonRow.add(clearButton);
        buttonRow.add(closeButton);
        topPanel.add(buttonRow, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.35);
        contentPane.add(splitPane, BorderLayout.CENTER);

        candidateModel = new DefaultListModel<>();
        candidateList = new JList<>(candidateModel);
        candidateList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidateList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane candidateScroll = new JScrollPane(candidateList);
        candidateScroll.setBorder(new TitledBorder("Candidate Methods (double-click to fill fields)"));
        splitPane.setTopComponent(candidateScroll);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Log"));
        splitPane.setBottomComponent(logScroll);

        candidateList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = candidateList.getSelectedValue();
                    if (selected != null) {
                        fillFieldsFromCandidate(selected);
                    }
                }
            }
        });

        scanButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onScan();
            }
        });

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRun();
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logArea.setText("");
            }
        });

        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void onScan() {
        Map<String, ClassNode> classes = getClasses();
        if (classes == null) {
            return;
        }
        log("Scanning for decryptor candidates...");
        candidateModel.clear();
        List<String> found = StringDecryptorUtils.findCandidateDecryptors(classes);
        if (found.isEmpty()) {
            log("No candidates found.");
        } else {
            for (String s : found) {
                candidateModel.addElement(s);
            }
            log("Found " + found.size() + " candidate(s). Double-click one to fill the fields.");
        }
    }

    private void onRun() {
        Map<String, ClassNode> classes = getClasses();
        if (classes == null) {
            return;
        }

        String owner = ownerField.getText().trim();
        String name = nameField.getText().trim();
        String desc = descField.getText().trim();

        if (owner.isEmpty() || name.isEmpty() || desc.isEmpty()) {
            log("ERROR: Owner, name, and descriptor must all be filled in.");
            return;
        }

        byte[] jarBytes = readJarBytes();
        if (jarBytes == null) {
            log("ERROR: Could not read the loaded JAR file from disk. Make sure the file is still accessible.");
            return;
        }

        log("Running decryptor: " + owner + "." + name + desc);
        runButton.setEnabled(false);
        scanButton.setEnabled(false);

        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return StringDecryptorUtils.decryptStrings(classes, owner, name, desc, jarBytes);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                scanButton.setEnabled(true);
                try {
                    int result = get();
                    if (result == -1) {
                        log("ERROR: Could not load or invoke the decryptor method.");
                        log("  - Check that owner, name, and descriptor are correct.");
                        log("  - The method must be static and return java.lang.String.");
                    } else if (result == 0) {
                        log("Done. No matching call sites found with constant arguments.");
                    } else {
                        log("Done. Replaced " + result + " encrypted string(s) with plaintext.");
                        jbm.refreshTree();
                    }
                } catch (Exception ex) {
                    log("ERROR: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void fillFieldsFromCandidate(String candidate) {
        int dotIdx = candidate.indexOf('.');
        int parenIdx = candidate.indexOf('(');
        if (dotIdx < 0 || parenIdx < 0 || parenIdx < dotIdx) {
            return;
        }
        String owner = candidate.substring(0, dotIdx);
        String name = candidate.substring(dotIdx + 1, parenIdx);
        String desc = candidate.substring(parenIdx);
        ownerField.setText(owner);
        nameField.setText(name);
        descField.setText(desc);
        log("Filled from candidate: " + candidate);
    }

    private Map<String, ClassNode> getClasses() {
        if (jbm.getJarArchive() == null || jbm.getJarArchive().getClasses() == null) {
            log("ERROR: No file loaded.");
            return null;
        }
        return jbm.getJarArchive().getClasses();
    }

    private byte[] readJarBytes() {
        try {
            File f = jbm.getFilePath();
            if (f != null && f.exists()) {
                return Files.readAllBytes(f.toPath());
            }
        } catch (Throwable t) {
            Main.INSTANCE.getLogger().err("Could not read JAR bytes: " + t.getMessage());
        }
        return null;
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void open(JByteMod jbm) {
        StringDecryptorDialog dialog = new StringDecryptorDialog(jbm);
        dialog.setLocationRelativeTo(jbm);
        dialog.setVisible(true);
    }
}
