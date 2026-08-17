package dev.joyel.mapping.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.mapping.AggregateMappingManager;
import dev.joyel.mapping.MappingApplier;
import dev.joyel.mapping.MappingSet;
import dev.joyel.mapping.format.MappingFormat;
import dev.joyel.mapping.format.MappingFormatRegistry;
import dev.joyel.mapping.format.MappingParseException;
import dev.joyel.mapping.gen.AlphabetNameGenerator;
import dev.joyel.mapping.gen.IncrementingNameGenerator;
import dev.joyel.mapping.gen.MappingGeneratorService;
import dev.joyel.mapping.gen.NameGenerator;
import me.grax.jbytemod.ui.tree.SortedTreeNode;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

public final class MappingDialog extends JDialog {

    private final JByteMod jbm;
    private final AggregateMappingManager aggregateManager;
    private final JTextArea statusArea;
    private final JLabel statsLabel;

    public MappingDialog(JByteMod jbm, AggregateMappingManager aggregateManager) {
        super(jbm, "Mapping / Renaming", false);
        this.jbm = jbm;
        this.aggregateManager = aggregateManager;

        setLayout(new BorderLayout(6, 6));
        setSize(560, 480);
        setLocationRelativeTo(jbm);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Import", buildImportPanel());
        tabs.addTab("Export", buildExportPanel());
        tabs.addTab("Auto-Rename", buildGeneratePanel());
        tabs.addTab("History", buildHistoryPanel());
        add(tabs, BorderLayout.CENTER);

        statusArea = new JTextArea(4, 40);
        statusArea.setEditable(false);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        add(new JScrollPane(statusArea), BorderLayout.SOUTH);

        statsLabel = new JLabel("No mappings applied.");
        statsLabel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(statsLabel, BorderLayout.NORTH);

        refreshStats();
    }

    private JPanel buildImportPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JComboBox<String> formatCombo = new JComboBox<String>();
        for (MappingFormat f : MappingFormatRegistry.getFormats()) {
            formatCombo.addItem(f.getName());
        }

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Format:"));
        top.add(formatCombo);
        panel.add(top, BorderLayout.NORTH);

        JButton fileBtn = new JButton("Import from file and apply...");
        fileBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MappingFormat fmt = MappingFormatRegistry.getFormats().get(formatCombo.getSelectedIndex());
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter(
                        fmt.getName() + " (*." + fmt.getFileExtension() + ")",
                        fmt.getFileExtension()));
                if (fc.showOpenDialog(MappingDialog.this) != JFileChooser.APPROVE_OPTION) return;
                try {
                    byte[] bytes = Files.readAllBytes(fc.getSelectedFile().toPath());
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    MappingSet parsed = fmt.parse(text);
                    applyMappings(parsed, "Imported from " + fc.getSelectedFile().getName());
                } catch (MappingParseException ex) {
                    status("Parse error: " + ex.getMessage());
                } catch (IOException ex) {
                    status("IO error: " + ex.getMessage());
                }
            }
        });

        JTextArea pasteArea = new JTextArea(10, 40);
        pasteArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        pasteArea.setToolTipText("Paste mapping text here");

        JButton pasteBtn = new JButton("Parse pasted text and apply");
        pasteBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MappingFormat fmt = MappingFormatRegistry.getFormats().get(formatCombo.getSelectedIndex());
                try {
                    MappingSet parsed = fmt.parse(pasteArea.getText());
                    applyMappings(parsed, "Pasted " + fmt.getName() + " mappings");
                } catch (MappingParseException ex) {
                    status("Parse error: " + ex.getMessage());
                }
            }
        });

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JScrollPane(pasteArea), BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnRow.add(fileBtn);
        btnRow.add(pasteBtn);
        center.add(btnRow, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JComboBox<String> formatCombo = new JComboBox<String>();
        for (MappingFormat f : MappingFormatRegistry.getFormats()) {
            formatCombo.addItem(f.getName());
        }

        JTextArea preview = new JTextArea(14, 40);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        preview.setEditable(false);

        JButton previewBtn = new JButton("Preview");
        previewBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MappingFormat fmt = MappingFormatRegistry.getFormats().get(formatCombo.getSelectedIndex());
                preview.setText(fmt.export(aggregateManager.getMerged()));
            }
        });

        JButton saveBtn = new JButton("Save to file...");
        saveBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MappingFormat fmt = MappingFormatRegistry.getFormats().get(formatCombo.getSelectedIndex());
                JFileChooser fc = new JFileChooser();
                fc.setSelectedFile(new File("mappings." + fmt.getFileExtension()));
                if (fc.showSaveDialog(MappingDialog.this) != JFileChooser.APPROVE_OPTION) return;
                try {
                    java.io.OutputStreamWriter fw = new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8);
                    fw.write(fmt.export(aggregateManager.getMerged()));
                    fw.close();
                    status("Saved to " + fc.getSelectedFile().getAbsolutePath());
                } catch (IOException ex) {
                    status("Save error: " + ex.getMessage());
                }
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Format:"));
        top.add(formatCombo);
        top.add(previewBtn);
        top.add(saveBtn);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(preview), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildGeneratePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        String[] genStyles = {"Incrementing (Class_1, field_1...)", "Alphabet (a, b, aa, ab...)"};
        JComboBox<String> styleCombo = new JComboBox<String>(genStyles);

        JTextField classPrefixField = new JTextField("Class_", 10);
        JTextField fieldPrefixField = new JTextField("field_", 10);
        JTextField methodPrefixField = new JTextField("method_", 10);

        JCheckBox renameClasses = new JCheckBox("Rename classes", true);
        JCheckBox renameFields = new JCheckBox("Rename fields", true);
        JCheckBox renameMethods = new JCheckBox("Rename methods", true);
        JCheckBox skipSpecial = new JCheckBox("Skip <init>/<clinit>/lambda$", true);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Style:"), gbc);
        gbc.gridx = 2; gbc.gridwidth = 3;
        panel.add(styleCombo, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Class prefix:"), gbc);
        gbc.gridx = 1;
        panel.add(classPrefixField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Field prefix:"), gbc);
        gbc.gridx = 1;
        panel.add(fieldPrefixField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Method prefix:"), gbc);
        gbc.gridx = 1;
        panel.add(methodPrefixField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4;
        panel.add(renameClasses, gbc);
        gbc.gridy = 5;
        panel.add(renameFields, gbc);
        gbc.gridy = 6;
        panel.add(renameMethods, gbc);
        gbc.gridy = 7;
        panel.add(skipSpecial, gbc);

        JButton generateBtn = new JButton("Generate and Apply");
        generateBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (jbm.getJarArchive() == null || jbm.getJarArchive().getClasses() == null) {
                    status("No JAR loaded.");
                    return;
                }
                NameGenerator gen;
                if (styleCombo.getSelectedIndex() == 0) {
                    gen = new IncrementingNameGenerator(
                            classPrefixField.getText(),
                            fieldPrefixField.getText(),
                            methodPrefixField.getText());
                } else {
                    gen = new AlphabetNameGenerator();
                }
                MappingSet generated = MappingGeneratorService.generate(
                        jbm.getJarArchive().getClasses(),
                        gen,
                        renameClasses.isSelected(),
                        renameFields.isSelected(),
                        renameMethods.isSelected(),
                        skipSpecial.isSelected());
                applyMappings(generated, "Auto-generated mappings");
            }
        });

        gbc.gridy = 8; gbc.gridwidth = 2;
        panel.add(generateBtn, gbc);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        DefaultListModel<String> listModel = new DefaultListModel<String>();
        JList<String> list = new JList<String>(listModel);
        rebuildHistoryModel(listModel);

        aggregateManager.addListener(new AggregateMappingManager.Listener() {
            public void onMappingsChanged(MappingSet merged) {
                rebuildHistoryModel(listModel);
                refreshStats();
            }
        });

        JButton undoBtn = new JButton("Undo last layer");
        undoBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (aggregateManager.getLayerCount() == 0) {
                    status("Nothing to undo.");
                    return;
                }
                int confirm = JOptionPane.showConfirmDialog(MappingDialog.this,
                        "This removes the last mapping layer from the aggregate history.\n" +
                        "Note: already-applied bytecode changes are not reversed automatically.\n" +
                        "Proceed?",
                        "Undo last layer", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    aggregateManager.pop();
                    status("Popped last mapping layer.");
                }
            }
        });

        JButton clearBtn = new JButton("Clear all history");
        clearBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int confirm = JOptionPane.showConfirmDialog(MappingDialog.this,
                        "Clear all mapping history?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    aggregateManager.clear();
                    status("Mapping history cleared.");
                }
            }
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnRow.add(undoBtn);
        btnRow.add(clearBtn);

        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(btnRow, BorderLayout.SOUTH);
        return panel;
    }

    private void rebuildHistoryModel(DefaultListModel<String> model) {
        model.clear();
        java.util.List<MappingSet> layers = aggregateManager.getLayers();
        for (int i = 0; i < layers.size(); i++) {
            MappingSet s = layers.get(i);
            model.addElement("Layer " + (i + 1) + "  [" + s.totalSize() + " entries]");
        }
    }

    private void applyMappings(MappingSet mappings, String description) {
        if (jbm.getJarArchive() == null || jbm.getJarArchive().getClasses() == null) {
            status("No JAR loaded.");
            return;
        }
        if (mappings.isEmpty()) {
            status("Mapping set is empty — nothing to apply.");
            return;
        }

        Map<String, ClassNode> remapped = MappingApplier.apply(jbm.getJarArchive().getClasses(), mappings);
        jbm.getJarArchive().getClasses().clear();
        jbm.getJarArchive().getClasses().putAll(remapped);

        aggregateManager.push(mappings);

        refreshClassTree();
        status(description + " applied. " + mappings.totalSize() + " entries: "
                + mappings.getClassMappings().size() + " classes, "
                + mappings.getFieldMappings().size() + " fields, "
                + mappings.getMethodMappings().size() + " methods.");
        refreshStats();
    }

    private void refreshClassTree() {
        if (jbm.getJarTree() == null) return;
        try {
            javax.swing.tree.DefaultTreeModel model =
                    (javax.swing.tree.DefaultTreeModel) jbm.getJarTree().getModel();
            SortedTreeNode root = new SortedTreeNode("Jar");
            for (String className : jbm.getJarArchive().getClasses().keySet()) {
                String[] parts = className.split("/");
                SortedTreeNode current = root;
                for (int i = 0; i < parts.length - 1; i++) {
                    SortedTreeNode found = null;
                    for (int j = 0; j < current.getChildCount(); j++) {
                        SortedTreeNode child = (SortedTreeNode) current.getChildAt(j);
                        if (parts[i].equals(child.getUserObject())) { found = child; break; }
                    }
                    if (found == null) { found = new SortedTreeNode(parts[i]); current.add(found); }
                    current = found;
                }
                current.add(new SortedTreeNode(parts[parts.length - 1]));
            }
            model.setRoot(root);
            model.reload();
        } catch (Exception ex) {
            status("Tree refresh error: " + ex.getMessage());
        }
    }

    private void refreshStats() {
        MappingSet merged = aggregateManager.getMerged();
        statsLabel.setText("Total mappings: " + merged.totalSize()
                + "  |  Classes: " + merged.getClassMappings().size()
                + "  |  Fields: " + merged.getFieldMappings().size()
                + "  |  Methods: " + merged.getMethodMappings().size()
                + "  |  Layers: " + aggregateManager.getLayerCount());
    }

    private void status(String msg) {
        statusArea.append(msg + "\n");
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }
}
