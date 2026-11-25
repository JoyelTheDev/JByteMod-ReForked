package dev.joyel.nbridge;

import org.objectweb.asm.Opcodes;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.prefs.Preferences;

public final class NativeBridgePanel extends JPanel {
    private static final String PREFERENCE_GHIDRA = "ghidraHome";

    private final NativeHost host;
    private final Preferences preferences = Preferences.userNodeForPackage(NativeBridgePanel.class);
    private final JLabel summaryLabel = new JLabel("Scanning...");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextField filterField = new JTextField(20);
    private final JButton rescanButton = new JButton("Rescan");
    private final JButton goToButton = new JButton("Go to method");
    private final JButton saveLibraryButton = new JButton("Save library...");
    private final JButton exportButton = new JButton("Export for Ghidra...");
    private final JButton analyzeButton = new JButton("Analyze with Ghidra");
    private final JButton openGhidraButton = new JButton("Open in Ghidra");
    private final JButton ghidraFolderButton = new JButton("Ghidra folder...");
    private final JTextArea detail = new JTextArea();
    private final MethodModel methodModel = new MethodModel();
    private final LibraryModel libraryModel = new LibraryModel();
    private final OrphanModel orphanModel = new OrphanModel();
    private final JTable methodTable = new JTable(methodModel);
    private final JTable libraryTable = new JTable(libraryModel);
    private final JTable orphanTable = new JTable(orphanModel);
    private final TableRowSorter<MethodModel> methodSorter = new TableRowSorter<>(methodModel);
    private final TableRowSorter<LibraryModel> librarySorter = new TableRowSorter<>(libraryModel);
    private final TableRowSorter<OrphanModel> orphanSorter = new TableRowSorter<>(orphanModel);
    private final JTabbedPane tabs = new JTabbedPane();
    private NativeScan scan = new NativeScan(List.of(), List.of(), List.of(), List.of());
    private Map<String, byte[]> resources = Map.of();
    private Path lastProject;
    private boolean busy;

    public NativeBridgePanel(NativeHost host) {
        this.host = host;
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(summaryLabel, BorderLayout.CENTER);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filters.add(new JLabel("Filter:"));
        filters.add(filterField);
        filters.add(rescanButton);
        top.add(filters, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        configureTable(methodTable, methodSorter, new int[]{260, 130, 170, 190, 160, 260, 80});
        configureTable(libraryTable, librarySorter, new int[]{300, 70, 110, 50, 80, 70, 90, 90, 200});
        configureTable(orphanTable, orphanSorter, new int[]{200, 330, 280, 260});
        methodTable.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());
        tabs.addTab("Native methods", new JScrollPane(methodTable));
        tabs.addTab("Libraries", new JScrollPane(libraryTable));
        tabs.addTab("Unmatched exports", new JScrollPane(orphanTable));

        detail.setEditable(false);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, new JScrollPane(detail));
        split.setResizeWeight(0.68);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        JPanel actions = new JPanel(new GridLayout(1, 0, 6, 0));
        actions.add(goToButton);
        actions.add(saveLibraryButton);
        actions.add(exportButton);
        actions.add(analyzeButton);
        actions.add(openGhidraButton);
        actions.add(ghidraFolderButton);
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(statusLabel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        rescanButton.addActionListener(event -> rescan());
        goToButton.addActionListener(event -> goToSelected());
        saveLibraryButton.addActionListener(event -> saveLibrary());
        exportButton.addActionListener(event -> exportForGhidra());
        analyzeButton.addActionListener(event -> analyzeWithGhidra());
        openGhidraButton.addActionListener(event -> openGhidra());
        ghidraFolderButton.addActionListener(event -> chooseGhidraFolder());
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applyFilter();
            }
        });
        methodTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showDetail();
            }
        });
        libraryTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showDetail();
            }
        });
        orphanTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showDetail();
            }
        });
        tabs.addChangeListener(event -> {
            applyFilter();
            showDetail();
            updateButtons();
        });
        methodTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    goToSelected();
                }
            }
        });
        updateButtons();
        rescan();
    }

    private void configureTable(JTable table, TableRowSorter<?> sorter, int[] widths) {
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        }
    }

    public void rescan() {
        if (busy) {
            return;
        }
        busy = true;
        summaryLabel.setText("Scanning...");
        updateButtons();
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() {
                Map<String, byte[]> snapshot = new HashMap<>(host.resources() == null ? Map.of() : host.resources());
                NativeScan result = NativeScanner.scan(new HashMap<>(host.classes()), snapshot);
                return new Object[]{result, snapshot};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                busy = false;
                try {
                    Object[] values = get();
                    scan = (NativeScan) values[0];
                    resources = (Map<String, byte[]>) values[1];
                    methodModel.set(scan.methods());
                    libraryModel.set(scan.libraries());
                    orphanModel.set(scan.orphans());
                    applyFilter();
                    summaryLabel.setText(summary());
                    detail.setText(guidance());
                    detail.setCaretPosition(0);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    summaryLabel.setText("Scan failed: " + messageOf(exception));
                }
                updateButtons();
            }
        }.execute();
    }

    private String summary() {
        int dynamic = 0;
        int missing = 0;
        for (NativeMethodInfo method : scan.methods()) {
            if (method.status() == MatchStatus.DYNAMIC) {
                dynamic++;
            } else if (method.status() != MatchStatus.EXPORTED) {
                missing++;
            }
        }
        return scan.methods().size() + " native method(s): " + scan.exportedCount() + " exported, " + dynamic
                + " maybe registered at runtime, " + missing + " unresolved - " + scan.libraries().size()
                + " native librar" + (scan.libraries().size() == 1 ? "y" : "ies") + ", "
                + scan.orphans().size() + " unmatched JNI export(s)";
    }

    private String guidance() {
        if (scan.methods().isEmpty() && scan.libraries().isEmpty()) {
            return "No native methods or native libraries were found in this archive.";
        }
        StringBuilder text = new StringBuilder("Select a row to see how a method maps to its native code.\n");
        if (scan.libraries().isEmpty() && !scan.methods().isEmpty()) {
            text.append("\nThis archive declares native methods but contains no native library. The code is probably\n"
                    + "loaded from disk or the system. Attached processes only expose classes, not their resources.\n");
        }
        return text.toString();
    }

    private void applyFilter() {
        String text = filterField.getText().trim().toLowerCase(Locale.ROOT);
        methodSorter.setRowFilter(text.isEmpty() ? null : new RowFilter<MethodModel, Integer>() {
            @Override
            public boolean include(Entry<? extends MethodModel, ? extends Integer> entry) {
                NativeMethodInfo method = entry.getModel().get(entry.getIdentifier());
                return method.owner().toLowerCase(Locale.ROOT).replace('/', '.').contains(text)
                        || method.name().toLowerCase(Locale.ROOT).contains(text)
                        || method.shortName().toLowerCase(Locale.ROOT).contains(text);
            }
        });
        librarySorter.setRowFilter(text.isEmpty() ? null : new RowFilter<LibraryModel, Integer>() {
            @Override
            public boolean include(Entry<? extends LibraryModel, ? extends Integer> entry) {
                return entry.getModel().get(entry.getIdentifier()).path().toLowerCase(Locale.ROOT).contains(text);
            }
        });
        orphanSorter.setRowFilter(text.isEmpty() ? null : new RowFilter<OrphanModel, Integer>() {
            @Override
            public boolean include(Entry<? extends OrphanModel, ? extends Integer> entry) {
                OrphanExport orphan = entry.getModel().get(entry.getIdentifier());
                return orphan.export().name().toLowerCase(Locale.ROOT).contains(text)
                        || orphan.describe().toLowerCase(Locale.ROOT).contains(text);
            }
        });
    }

    private NativeMethodInfo selectedMethod() {
        int row = methodTable.getSelectedRow();
        return row < 0 ? null : methodModel.get(methodTable.convertRowIndexToModel(row));
    }

    private NativeLibrary selectedLibrary() {
        int row = libraryTable.getSelectedRow();
        return row < 0 ? null : libraryModel.get(libraryTable.convertRowIndexToModel(row));
    }

    private OrphanExport selectedOrphan() {
        int row = orphanTable.getSelectedRow();
        return row < 0 ? null : orphanModel.get(orphanTable.convertRowIndexToModel(row));
    }

    private void showDetail() {
        int tab = tabs.getSelectedIndex();
        if (tab == 0) {
            NativeMethodInfo method = selectedMethod();
            if (method != null) {
                detail.setText(describeMethod(method));
                detail.setCaretPosition(0);
                updateButtons();
                return;
            }
        } else if (tab == 1) {
            NativeLibrary library = selectedLibrary();
            if (library != null) {
                detail.setText(describeLibrary(library));
                detail.setCaretPosition(0);
                updateButtons();
                return;
            }
        } else {
            OrphanExport orphan = selectedOrphan();
            if (orphan != null) {
                detail.setText(describeOrphan(orphan));
                detail.setCaretPosition(0);
                updateButtons();
                return;
            }
        }
        detail.setText(guidance());
        detail.setCaretPosition(0);
        updateButtons();
    }

    private String describeMethod(NativeMethodInfo method) {
        StringBuilder text = new StringBuilder();
        text.append("Method:   ").append(method.owner().replace('/', '.')).append('.').append(method.name())
                .append(method.descriptor()).append('\n')
                .append("Flags:    ").append(flags(method.methodNode().access)).append('\n')
                .append("Status:   ").append(method.status().label()).append("\n\n")
                .append("JNI short name: ").append(method.shortName()).append('\n')
                .append("JNI long name:  ").append(method.longName()).append("\n\n");
        if (!method.libraryHints().isEmpty()) {
            text.append("Loads library: ").append(String.join(", ", method.libraryHints())).append("\n\n");
        }
        if (method.matches().isEmpty()) {
            switch (method.status()) {
                case DYNAMIC -> text.append("""
                        No export matches. A library with JNI_OnLoad exists, so this method is most likely bound
                        with RegisterNatives. Use "Export for Ghidra..." or "Analyze with Ghidra": the bundled script
                        searches the JNINativeMethod tables and labels the function that implements it.
                        """);
                case NOT_FOUND -> text.append("""
                        No library exports this method and none has JNI_OnLoad. It may belong to a library that is
                        loaded from outside the archive, or the symbol names may have been stripped or renamed.
                        """);
                case NO_LIBRARY -> text.append("""
                        This archive has no native library. The implementation is loaded from elsewhere.
                        """);
                default -> {
                }
            }
        } else {
            text.append("Matches:\n");
            for (NativeMatch match : method.matches()) {
                text.append("  ").append(match.library().path()).append(" [").append(match.library().architecture())
                        .append("]\n      ").append(match.export().name()).append("  at 0x")
                        .append(Long.toHexString(match.export().address()))
                        .append(match.longForm() ? "  (long name)" : "  (short name)")
                        .append(match.hinted() ? "  - named by System.loadLibrary" : "");
                if (match.ambiguous()) {
                    text.append("  - overloaded method: short name is ambiguous");
                }
                text.append('\n');
            }
        }
        return text.toString();
    }

    private String describeLibrary(NativeLibrary library) {
        StringBuilder text = new StringBuilder();
        text.append("Library:  ").append(library.path()).append('\n')
                .append("Format:   ").append(library.format()).append(", ").append(library.architecture()).append(", ")
                .append(library.bits()).append("-bit\n")
                .append("Size:     ").append(library.size()).append(" bytes\n")
                .append("Exports:  ").append(library.exports().size()).append(" (").append(library.jniExportCount())
                .append(" JNI)\n")
                .append("JNI_OnLoad:   ").append(library.jniOnLoad() ? "yes - may register natives at runtime" : "no").append('\n')
                .append("JNI_OnUnload: ").append(library.jniOnUnload() ? "yes" : "no").append('\n');
        if (library.error() != null) {
            text.append('\n').append(library.error()).append('\n');
        }
        int shown = 0;
        text.append("\nExported symbols:\n");
        for (NativeExport export : library.exports()) {
            if (shown++ >= 200) {
                text.append("  ... ").append(library.exports().size() - 200).append(" more\n");
                break;
            }
            text.append("  ").append(export.name()).append("  0x").append(Long.toHexString(export.address())).append('\n');
        }
        return text.toString();
    }

    private String describeOrphan(OrphanExport orphan) {
        return "Library:  " + orphan.library().path() + '\n'
                + "Export:   " + orphan.export().name() + "  at 0x" + Long.toHexString(orphan.export().address()) + '\n'
                + "Decoded:  " + orphan.describe() + (orphan.arguments() == null ? "" : "  args: " + orphan.arguments()) + '\n'
                + "Result:   " + orphan.reason() + "\n\n"
                + "This function follows the JNI naming scheme but no native method in the archive matches it.\n"
                + "Obfuscators often rename Java methods without renaming the library; compare the class below\n"
                + "for a native method with the same parameters.\n";
    }

    private static String flags(int access) {
        List<String> flags = new ArrayList<>();
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            flags.add("public");
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            flags.add("protected");
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            flags.add("private");
        }
        if ((access & Opcodes.ACC_STATIC) != 0) {
            flags.add("static");
        }
        if ((access & Opcodes.ACC_FINAL) != 0) {
            flags.add("final");
        }
        if ((access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            flags.add("synchronized");
        }
        flags.add("native");
        return String.join(" ", flags);
    }

    private void updateButtons() {
        boolean idle = !busy;
        rescanButton.setEnabled(idle);
        goToButton.setEnabled(idle && tabs.getSelectedIndex() == 0 && selectedMethod() != null);
        saveLibraryButton.setEnabled(idle && libraryForSave() != null);
        exportButton.setEnabled(idle && !scan.libraries().isEmpty());
        analyzeButton.setEnabled(idle && !scan.libraries().isEmpty());
        openGhidraButton.setEnabled(idle && ghidraHome() != null);
        ghidraFolderButton.setEnabled(idle);
    }

    private NativeLibrary libraryForSave() {
        if (tabs.getSelectedIndex() == 1) {
            return selectedLibrary();
        }
        if (tabs.getSelectedIndex() == 0) {
            NativeMethodInfo method = selectedMethod();
            if (method != null && !method.matches().isEmpty()) {
                return method.matches().get(0).library();
            }
        }
        if (tabs.getSelectedIndex() == 2) {
            OrphanExport orphan = selectedOrphan();
            return orphan == null ? null : orphan.library();
        }
        return null;
    }

    private void goToSelected() {
        NativeMethodInfo method = selectedMethod();
        if (method != null) {
            host.goTo(method.classNode(), method.methodNode());
        }
    }

    private void saveLibrary() {
        NativeLibrary library = libraryForSave();
        if (library == null) {
            return;
        }
        byte[] data = resources.get(library.path());
        if (data == null) {
            statusLabel.setText("The library data is no longer available.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(library.fileName()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.write(chooser.getSelectedFile().toPath(), data);
            statusLabel.setText("Saved " + chooser.getSelectedFile().getName());
        } catch (Exception failure) {
            error("Could not save the library: " + failure.getMessage());
        }
    }

    private List<NativeLibrary> librariesToExport() {
        NativeLibrary selected = tabs.getSelectedIndex() == 1 ? selectedLibrary() : null;
        return selected == null ? scan.libraries() : List.of(selected);
    }

    private void exportForGhidra() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a folder for the Ghidra files");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path directory = chooser.getSelectedFile().toPath();
        List<NativeLibrary> libraries = librariesToExport();
        runTask("Exporting for Ghidra...", () -> GhidraExporter.export(scan, libraries, resources, directory), result -> {
            GhidraExporter.Result exported = (GhidraExporter.Result) result;
            JOptionPane.showMessageDialog(this, "Wrote " + exported.libraries().size() + " librar"
                    + (exported.libraries().size() == 1 ? "y" : "ies") + " and the labeling script to\n"
                    + exported.directory() + "\n\n" + exported.exportRows() + " exported and "
                    + exported.registerRows() + " RegisterNatives candidate(s) are listed.\n\n"
                    + "In Ghidra: import the libraries, then open Window > Script Manager, add this folder\n"
                    + "to the script directories and run " + GhidraExporter.SCRIPT_NAME + " on each program.",
                    "Export for Ghidra", JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("Exported to " + exported.directory());
        });
    }

    private Path ghidraHome() {
        String value = preferences.get(PREFERENCE_GHIDRA, "");
        if (value.isBlank()) {
            return null;
        }
        Path home = Path.of(value);
        return GhidraRunner.isValidHome(home) ? home : null;
    }

    private void chooseGhidraFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select the Ghidra installation folder (contains ghidraRun)");
        String current = preferences.get(PREFERENCE_GHIDRA, "");
        if (!current.isBlank()) {
            chooser.setCurrentDirectory(new File(current).getParentFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path home = chooser.getSelectedFile().toPath();
        if (!GhidraRunner.isValidHome(home)) {
            error("That folder does not look like a Ghidra installation. Select the folder that contains ghidraRun and the support folder.");
            return;
        }
        preferences.put(PREFERENCE_GHIDRA, home.toString());
        statusLabel.setText("Ghidra folder set to " + home);
        updateButtons();
    }

    private void analyzeWithGhidra() {
        Path home = ghidraHome();
        if (home == null) {
            chooseGhidraFolder();
            home = ghidraHome();
            if (home == null) {
                return;
            }
        }
        Path root = Path.of(System.getProperty("user.home"), ".jbytemod", "ghidra");
        Path exportDirectory = root.resolve("export");
        Path projectDirectory = root.resolve("project");
        List<NativeLibrary> libraries = librariesToExport();
        Path ghidraHome = home;
        JTextArea log = new JTextArea(18, 90);
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        javax.swing.JDialog logDialog = new javax.swing.JDialog(SwingUtilities.getWindowAncestor(this), "Ghidra analysis",
                java.awt.Dialog.ModalityType.MODELESS);
        logDialog.setContentPane(new JScrollPane(log));
        logDialog.pack();
        logDialog.setLocationRelativeTo(this);
        logDialog.setVisible(true);
        runTask("Running Ghidra headless analysis...", () -> {
            GhidraExporter.Result exported = GhidraExporter.export(scan, libraries, resources, exportDirectory);
            int exit = GhidraRunner.analyze(ghidraHome, exported, projectDirectory, "JByteMod", line ->
                    SwingUtilities.invokeLater(() -> {
                        log.append(line + "\n");
                        log.setCaretPosition(log.getDocument().getLength());
                    }));
            return new Object[]{exit, GhidraRunner.projectFile(projectDirectory, "JByteMod")};
        }, result -> {
            Object[] values = (Object[]) result;
            int exit = (Integer) values[0];
            lastProject = (Path) values[1];
            if (exit == 0) {
                statusLabel.setText("Ghidra finished. The project is ready to open.");
                int choice = JOptionPane.showConfirmDialog(this, "Analysis finished. Open Ghidra now?",
                        "Ghidra", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    openGhidra();
                }
            } else {
                statusLabel.setText("Ghidra exited with code " + exit + " - see the log window.");
            }
        });
    }

    private void openGhidra() {
        Path home = ghidraHome();
        if (home == null) {
            chooseGhidraFolder();
            home = ghidraHome();
            if (home == null) {
                return;
            }
        }
        Path project = lastProject;
        if (project == null) {
            Path candidate = GhidraRunner.projectFile(
                    Path.of(System.getProperty("user.home"), ".jbytemod", "ghidra", "project"), "JByteMod");
            project = Files.isRegularFile(candidate) ? candidate : null;
        }
        try {
            GhidraRunner.launchGui(home, project);
            statusLabel.setText("Launching Ghidra...");
        } catch (Exception failure) {
            error("Could not start Ghidra: " + failure.getMessage());
        }
    }

    private interface Task {
        Object run() throws Exception;
    }

    private void runTask(String message, Task task, java.util.function.Consumer<Object> onSuccess) {
        if (busy) {
            return;
        }
        busy = true;
        statusLabel.setText(message);
        updateButtons();
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    onSuccess.accept(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    statusLabel.setText("Failed: " + messageOf(exception));
                    error(messageOf(exception));
                }
                updateButtons();
            }
        }.execute();
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Native bridge", JOptionPane.ERROR_MESSAGE);
    }

    private static String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (!selected) {
                String text = String.valueOf(value);
                if (text.startsWith("Exported")) {
                    component.setForeground(new Color(0x2E8B57));
                } else if (text.startsWith("Maybe")) {
                    component.setForeground(new Color(0xC77700));
                } else {
                    component.setForeground(new Color(0xC0392B));
                }
            }
            return component;
        }
    }

    private static final class MethodModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Class", "Method", "Descriptor", "Status", "Library", "Export", "Address"};
        private List<NativeMethodInfo> rows = List.of();

        void set(List<NativeMethodInfo> methods) {
            rows = methods;
            fireTableDataChanged();
        }

        NativeMethodInfo get(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            NativeMethodInfo method = rows.get(row);
            NativeMatch first = method.matches().isEmpty() ? null : method.matches().get(0);
            return switch (column) {
                case 0 -> method.owner().replace('/', '.');
                case 1 -> method.name();
                case 2 -> method.descriptor();
                case 3 -> method.status().label();
                case 4 -> first == null ? "" : first.library().fileName()
                        + (method.matches().size() > 1 ? " (+" + (method.matches().size() - 1) + ")" : "");
                case 5 -> first == null ? "" : first.export().name();
                default -> first == null ? "" : "0x" + Long.toHexString(first.export().address());
            };
        }
    }

    private static final class LibraryModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Path", "Format", "Architecture", "Bits", "Size", "Exports", "JNI exports",
                "JNI_OnLoad", "Notes"};
        private List<NativeLibrary> rows = List.of();

        void set(List<NativeLibrary> libraries) {
            rows = libraries;
            fireTableDataChanged();
        }

        NativeLibrary get(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 3, 4, 5, 6 -> Long.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            NativeLibrary library = rows.get(row);
            return switch (column) {
                case 0 -> library.path();
                case 1 -> library.format();
                case 2 -> library.architecture();
                case 3 -> (long) library.bits();
                case 4 -> library.size();
                case 5 -> (long) library.exports().size();
                case 6 -> (long) library.jniExportCount();
                case 7 -> library.jniOnLoad() ? "yes" : "no";
                default -> library.error() == null ? "" : library.error();
            };
        }
    }

    private static final class OrphanModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Library", "Export", "Decoded Java name", "Reason"};
        private List<OrphanExport> rows = List.of();

        void set(List<OrphanExport> orphans) {
            rows = orphans;
            fireTableDataChanged();
        }

        OrphanExport get(int row) {
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            OrphanExport orphan = rows.get(row);
            return switch (column) {
                case 0 -> orphan.library().fileName();
                case 1 -> orphan.export().name();
                case 2 -> orphan.describe();
                default -> orphan.reason();
            };
        }
    }
}
