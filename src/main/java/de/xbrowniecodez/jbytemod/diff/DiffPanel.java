package de.xbrowniecodez.jbytemod.diff;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.utils.ErrorDisplay;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DiffPanel extends JPanel {

    private final JByteMod jbm;
    private DiffResult currentDiff;
    private BytecodePatch currentPatch;

    private JTable diffTable;
    private DefaultTableModel diffTableModel;
    private JLabel summaryLabel;
    private JTextArea detailArea;
    private JComboBox<String> filterCombo;

    public DiffPanel(JByteMod jbm) {
        this.jbm = jbm;
        setLayout(new BorderLayout(4, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));
        buildUI();
    }

    private void buildUI() {
        add(buildToolbar(), BorderLayout.NORTH);

        diffTableModel = new DefaultTableModel(new Object[]{"Type", "Class", "Method/Field", "Descriptor", "Index", "Old", "New"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        diffTable = new JTable(diffTableModel);
        diffTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        diffTable.setRowHeight(18);
        diffTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        diffTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        diffTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        diffTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        diffTable.getColumnModel().getColumn(4).setPreferredWidth(50);
        diffTable.getColumnModel().getColumn(5).setPreferredWidth(200);
        diffTable.getColumnModel().getColumn(6).setPreferredWidth(200);
        diffTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = diffTable.getSelectedRow();
                if (row >= 0) showDetail(row);
            }
        });

        detailArea = new JTextArea(5, 40);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setEditable(false);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(diffTable), new JScrollPane(detailArea));
        split.setResizeWeight(0.75);
        add(split, BorderLayout.CENTER);

        summaryLabel = new JLabel("No diff loaded.");
        summaryLabel.setBorder(new EmptyBorder(2, 4, 2, 4));
        add(summaryLabel, BorderLayout.SOUTH);
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton diffBtn = new JButton("Diff vs JAR...");
        diffBtn.addActionListener(e -> openDiffDialog());
        toolbar.add(diffBtn);

        JButton savePatchBtn = new JButton("Save Patch...");
        savePatchBtn.addActionListener(e -> savePatch());
        toolbar.add(savePatchBtn);

        JButton loadPatchBtn = new JButton("Load & Apply Patch...");
        loadPatchBtn.addActionListener(e -> loadAndApplyPatch());
        toolbar.add(loadPatchBtn);

        JButton exportTextBtn = new JButton("Export as Text...");
        exportTextBtn.addActionListener(e -> exportDiffAsText());
        toolbar.add(exportTextBtn);

        toolbar.add(new JSeparator(SwingConstants.VERTICAL));

        filterCombo = new JComboBox<>(new String[]{"All", "ADDED", "REMOVED", "MODIFIED", "METHOD_ADDED", "METHOD_REMOVED", "FIELD_ADDED", "FIELD_REMOVED", "CLASS_ACCESS_CHANGED"});
        filterCombo.addActionListener(e -> applyFilter());
        toolbar.add(new JLabel("Filter:"));
        toolbar.add(filterCombo);

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> clearDiff());
        toolbar.add(clearBtn);

        return toolbar;
    }

    private void openDiffDialog() {
        if (jbm.getJarArchive() == null) {
            JOptionPane.showMessageDialog(this, "No JAR currently loaded in JByteModX.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Select Modified JAR to Diff Against");
        fc.setFileFilter(new FileNameExtensionFilter("JAR / Class Files", "jar", "class"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File modFile = fc.getSelectedFile();
        SwingWorker<DiffResult, Void> worker = new SwingWorker<DiffResult, Void>() {
            @Override
            protected DiffResult doInBackground() throws Exception {
                Map<String, ClassNode> original = jbm.getJarArchive().getClasses();
                JarArchive modArchive = new JarArchive(jbm, modFile);
                while (modArchive.getClasses() == null) {
                    Thread.sleep(100);
                }
                Map<String, ClassNode> modified = modArchive.getClasses();
                BytecodeDiffer differ = new BytecodeDiffer();
                return differ.diff(original, modified,
                        jbm.getLastEditFile(), modFile.getName());
            }

            @Override
            protected void done() {
                try {
                    currentDiff = get();
                    currentPatch = new PatchBuilder().buildFromDiff(currentDiff,
                            jbm.getJarArchive().getClasses(), "patch_" + System.currentTimeMillis());
                    populateTable(currentDiff);
                    summaryLabel.setText(currentDiff.getSummary());
                } catch (Exception e) {
                    new ErrorDisplay(e);
                }
            }
        };
        worker.execute();
        summaryLabel.setText("Diffing...");
    }

    private void populateTable(DiffResult diff) {
        diffTableModel.setRowCount(0);
        for (BytecodeDiff d : diff.getDiffs()) {
            diffTableModel.addRow(new Object[]{
                    d.getType().name(),
                    d.getClassName(),
                    d.getMethodName() != null ? d.getMethodName() : "",
                    d.getMethodDesc() != null ? d.getMethodDesc() : "",
                    d.getInstructionIndex() >= 0 ? d.getInstructionIndex() : "",
                    d.getOldValue() != null ? d.getOldValue() : "",
                    d.getNewValue() != null ? d.getNewValue() : ""
            });
        }
    }

    private void applyFilter() {
        if (currentDiff == null) return;
        String selected = (String) filterCombo.getSelectedItem();
        diffTableModel.setRowCount(0);
        for (BytecodeDiff d : currentDiff.getDiffs()) {
            if (selected.equals("All") || d.getType().name().equals(selected)) {
                diffTableModel.addRow(new Object[]{
                        d.getType().name(), d.getClassName(),
                        d.getMethodName() != null ? d.getMethodName() : "",
                        d.getMethodDesc() != null ? d.getMethodDesc() : "",
                        d.getInstructionIndex() >= 0 ? d.getInstructionIndex() : "",
                        d.getOldValue() != null ? d.getOldValue() : "",
                        d.getNewValue() != null ? d.getNewValue() : ""
                });
            }
        }
        long count = currentDiff.getDiffs().stream()
                .filter(d -> selected.equals("All") || d.getType().name().equals(selected))
                .count();
        summaryLabel.setText(currentDiff.getSummary() + " | Showing: " + count);
    }

    private void showDetail(int row) {
        if (currentDiff == null || row < 0 || row >= currentDiff.getDiffs().size()) return;
        BytecodeDiff d = currentDiff.getDiffs().get(row);
        StringBuilder sb = new StringBuilder();
        sb.append("Type:        ").append(d.getType()).append("\n");
        sb.append("Class:       ").append(d.getClassName()).append("\n");
        if (d.getMethodName() != null) sb.append("Method:      ").append(d.getMethodName()).append("\n");
        if (d.getMethodDesc() != null) sb.append("Descriptor:  ").append(d.getMethodDesc()).append("\n");
        if (d.getInstructionIndex() >= 0) sb.append("Insn Index:  ").append(d.getInstructionIndex()).append("\n");
        if (d.getOldValue() != null) sb.append("Old:         ").append(d.getOldValue()).append("\n");
        if (d.getNewValue() != null) sb.append("New:         ").append(d.getNewValue()).append("\n");
        detailArea.setText(sb.toString());
    }

    private void savePatch() {
        if (currentPatch == null) {
            JOptionPane.showMessageDialog(this, "No patch generated. Run a diff first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String desc = JOptionPane.showInputDialog(this, "Enter patch description (optional):", "Patch Description", JOptionPane.PLAIN_MESSAGE);
        if (desc != null) currentPatch.setDescription(desc);

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save Patch File");
        fc.setFileFilter(new FileNameExtensionFilter("JByteMod Patch (*.jbpatch)", "jbpatch"));
        fc.setSelectedFile(new File(currentPatch.getPatchName() + ".jbpatch"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File out = fc.getSelectedFile();
        if (!out.getName().endsWith(".jbpatch")) out = new File(out.getAbsolutePath() + ".jbpatch");

        try {
            currentPatch.save(out);
            JOptionPane.showMessageDialog(this, "Patch saved to:\n" + out.getAbsolutePath(), "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            new ErrorDisplay(e);
        }
    }

    private void loadAndApplyPatch() {
        if (jbm.getJarArchive() == null) {
            JOptionPane.showMessageDialog(this, "No JAR loaded.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Load Patch File");
        fc.setFileFilter(new FileNameExtensionFilter("JByteMod Patch (*.jbpatch)", "jbpatch"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File patchFile = fc.getSelectedFile();
        try {
            BytecodePatch patch = BytecodePatch.load(patchFile);
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Apply patch: " + patch.getPatchName() + "\nTarget: " + patch.getTargetJar()
                    + (patch.getDescription() != null ? "\nDesc: " + patch.getDescription() : "")
                    + "\n\nApply to current JAR?",
                    "Confirm Patch", JOptionPane.YES_NO_OPTION);

            if (confirm != JOptionPane.YES_OPTION) return;

            PatchApplier applier = new PatchApplier();
            PatchApplier.PatchApplyResult result = applier.apply(patch, jbm.getJarArchive().getClasses());

            jbm.refreshTree();

            StringBuilder report = new StringBuilder();
            report.append(result.getSummary()).append("\n\n");
            if (!result.getFailed().isEmpty()) {
                report.append("FAILED:\n");
                result.getFailed().forEach(f -> report.append("  ").append(f).append("\n"));
            }
            if (!result.getSkipped().isEmpty()) {
                report.append("SKIPPED:\n");
                result.getSkipped().forEach(s -> report.append("  ").append(s).append("\n"));
            }
            report.append("\nAPPLIED:\n");
            result.getApplied().forEach(a -> report.append("  ").append(a).append("\n"));

            JTextArea reportArea = new JTextArea(report.toString(), 20, 60);
            reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            reportArea.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(reportArea),
                    result.isSuccess() ? "Patch Applied" : "Patch Applied with Errors",
                    result.isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);

        } catch (Exception e) {
            new ErrorDisplay(e);
        }
    }

    private void exportDiffAsText() {
        if (currentDiff == null) {
            JOptionPane.showMessageDialog(this, "No diff loaded.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Diff as Text");
        fc.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
        fc.setSelectedFile(new File("diff_" + System.currentTimeMillis() + ".txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File out = fc.getSelectedFile();
        try {
            if (currentPatch != null) {
                currentPatch.saveAsText(out);
            } else {
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(out));
                pw.println("Diff: " + currentDiff.getOriginalJar() + " vs " + currentDiff.getModifiedJar());
                pw.println(currentDiff.getSummary());
                pw.println();
                for (BytecodeDiff d : currentDiff.getDiffs()) {
                    pw.println(d.toString());
                }
                pw.close();
            }
            JOptionPane.showMessageDialog(this, "Exported to:\n" + out.getAbsolutePath(), "Exported", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            new ErrorDisplay(e);
        }
    }

    private void clearDiff() {
        currentDiff = null;
        currentPatch = null;
        diffTableModel.setRowCount(0);
        detailArea.setText("");
        summaryLabel.setText("No diff loaded.");
    }

    public DiffResult getCurrentDiff() { return currentDiff; }
    public BytecodePatch getCurrentPatch() { return currentPatch; }

    public void clearDiffIfStale() {
        summaryLabel.setText("New file loaded — previous diff cleared.");
        clearDiff();
    }
}
