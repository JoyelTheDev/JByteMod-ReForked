package dev.joyel.attach;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class DumpPanel extends JPanel {
    private static final int POLL_MILLIS = 1000;

    private final RemoteJarArchive archive;
    private final EntryTableModel model = new EntryTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<EntryTableModel> sorter = new TableRowSorter<>(model);
    private final JTextArea detail = new JTextArea();
    private final JToggleButton recordButton = new JToggleButton("Start capture");
    private final JButton scanButton = new JButton("Scan loaded classes");
    private final JButton clearButton = new JButton("Clear");
    private final JCheckBox noCodeSourceBox = new JCheckBox("Record only classes without a code source");
    private final JCheckBox lambdaBox = new JCheckBox("Include lambdas");
    private final JSpinner limitSpinner = new JSpinner(new SpinnerNumberModel(256, 16, 2048, 16));
    private final JTextField filterField = new JTextField(18);
    private final JCheckBox runtimeOnlyBox = new JCheckBox("Hide classes loaded from a file or jar", true);
    private final JButton exportJarButton = new JButton("Export JAR...");
    private final JButton exportFolderButton = new JButton("Export folder...");
    private final JButton copyButton = new JButton("Copy class names");
    private final JLabel statusLabel = new JLabel("Connecting...");
    private final Timer timer;
    private long sequence;
    private boolean polling;
    private boolean busy;
    private boolean updatingToggle;
    private boolean closed;

    public DumpPanel(RemoteJarArchive archive) {
        this.archive = archive;
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.add(recordButton);
        controls.add(scanButton);
        controls.add(clearButton);
        controls.add(noCodeSourceBox);
        controls.add(lambdaBox);
        controls.add(new JLabel("Memory limit (MB):"));
        controls.add(limitSpinner);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(new JLabel("Filter:"));
        filters.add(filterField);
        filters.add(runtimeOnlyBox);
        top.add(controls, BorderLayout.NORTH);
        top.add(filters, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(320);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(200);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(5).setPreferredWidth(50);
        table.getColumnModel().getColumn(6).setPreferredWidth(90);
        table.getColumnModel().getColumn(7).setPreferredWidth(60);
        table.getColumnModel().getColumn(8).setPreferredWidth(100);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(0).setCellRenderer(right);
        table.getColumnModel().getColumn(4).setCellRenderer(right);
        table.getColumnModel().getColumn(5).setCellRenderer(right);

        detail.setEditable(false);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detail.setLineWrap(false);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), new JScrollPane(detail));
        split.setResizeWeight(0.7);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(exportJarButton);
        actions.add(exportFolderButton);
        actions.add(copyButton);
        bottom.add(actions, BorderLayout.WEST);
        bottom.add(statusLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        recordButton.addActionListener(event -> toggleRecording());
        scanButton.addActionListener(event -> scan());
        clearButton.addActionListener(event -> clearAll());
        exportJarButton.addActionListener(event -> export(true));
        exportFolderButton.addActionListener(event -> export(false));
        copyButton.addActionListener(event -> copyNames());
        runtimeOnlyBox.addActionListener(event -> applyFilter());
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
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showDetail();
            }
        });

        timer = new Timer(POLL_MILLIS, event -> poll());
        timer.setRepeats(true);
        applyFilter();
        showDetail();
        syncWithAgent();
    }

    public void shutdown() {
        closed = true;
        timer.stop();
    }

    public int visibleRowCount() {
        return table.getRowCount();
    }

    public int totalRowCount() {
        return model.getRowCount();
    }

    private void syncWithAgent() {
        runRemote("Reading capture state...", () -> {
            CaptureStatus status = archive.captureStatus();
            CaptureListing listing = archive.captureList(0);
            return new Object[]{status, listing};
        }, result -> {
            Object[] values = (Object[]) result;
            CaptureStatus status = (CaptureStatus) values[0];
            CaptureListing listing = (CaptureListing) values[1];
            noCodeSourceBox.setSelected(status.onlyNoCodeSource());
            lambdaBox.setSelected(status.includeLambdas());
            limitSpinner.setValue((int) Math.max(16, Math.min(2048, status.maxBytes() / (1024 * 1024))));
            merge(listing);
            applyStatus(status);
            timer.start();
        });
    }

    private CaptureOptions options() {
        return new CaptureOptions(noCodeSourceBox.isSelected(), lambdaBox.isSelected(), (Integer) limitSpinner.getValue());
    }

    private void toggleRecording() {
        if (updatingToggle) {
            return;
        }
        boolean start = recordButton.isSelected();
        CaptureOptions options = options();
        runRemote(start ? "Starting capture..." : "Stopping capture...", () -> start
                ? archive.captureStart(options) : archive.captureStop(), result -> applyStatus((CaptureStatus) result));
    }

    private void scan() {
        boolean lambdas = lambdaBox.isSelected();
        runRemote("Scanning loaded classes...", () -> {
            int added = archive.captureScan(lambdas);
            CaptureListing listing = archive.captureList(sequence);
            return new Object[]{added, listing};
        }, result -> {
            Object[] values = (Object[]) result;
            merge((CaptureListing) values[1]);
            statusLabel.setText("Scan added " + values[0] + " runtime-defined class(es)");
        });
    }

    private void clearAll() {
        runRemote("Clearing...", archive::captureClear, result -> {
            CaptureStatus status = (CaptureStatus) result;
            model.clear();
            sequence = status.sequence();
            applyStatus(status);
            showDetail();
        });
    }

    private void poll() {
        if (closed || polling || busy) {
            return;
        }
        if (archive.isFrozen()) {
            statusLabel.setText("Target is frozen - resume it to continue capturing");
            return;
        }
        polling = true;
        long since = sequence;
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                CaptureListing listing = archive.captureList(since);
                CaptureStatus status = archive.captureStatus();
                return new Object[]{listing, status};
            }

            @Override
            protected void done() {
                polling = false;
                if (closed) {
                    return;
                }
                try {
                    Object[] values = get();
                    merge((CaptureListing) values[0]);
                    applyStatus((CaptureStatus) values[1]);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    timer.stop();
                    statusLabel.setText("Connection lost: " + messageOf(exception));
                    setControlsEnabled(false);
                }
            }
        }.execute();
    }

    private void merge(CaptureListing listing) {
        sequence = Math.max(sequence, listing.sequence());
        if (!listing.entries().isEmpty()) {
            model.merge(listing.entries());
            applyFilter();
        }
    }

    private void applyStatus(CaptureStatus status) {
        updatingToggle = true;
        try {
            recordButton.setSelected(status.recording());
            recordButton.setText(status.recording() ? "Stop capture" : "Start capture");
        } finally {
            updatingToggle = false;
        }
        StringBuilder text = new StringBuilder();
        text.append(status.recording() ? "Recording" : "Stopped").append(" - ")
                .append(status.count()).append(" classes, ").append(formatSize(status.totalBytes()))
                .append(" of ").append(formatSize(status.maxBytes()));
        if (status.overflow()) {
            text.append(" - memory limit reached, ").append(status.dropped()).append(" class(es) dropped");
        }
        int hidden = model.getRowCount() - table.getRowCount();
        if (hidden > 0) {
            text.append(" - ").append(hidden).append(" hidden by filter");
        }
        statusLabel.setText(text.toString());
    }

    private void applyFilter() {
        String text = filterField.getText().trim().toLowerCase(Locale.ROOT);
        boolean runtimeOnly = runtimeOnlyBox.isSelected();
        sorter.setRowFilter(new RowFilter<EntryTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends EntryTableModel, ? extends Integer> entry) {
                CapturedClassInfo info = entry.getModel().get(entry.getIdentifier());
                if (runtimeOnly && info.origin() != null) {
                    return false;
                }
                if (text.isEmpty()) {
                    return true;
                }
                return info.name().toLowerCase(Locale.ROOT).replace('/', '.').contains(text)
                        || info.loaderName().toLowerCase(Locale.ROOT).contains(text)
                        || (info.thread() != null && info.thread().toLowerCase(Locale.ROOT).contains(text));
            }
        });
    }

    private List<CapturedClassInfo> selectedOrVisible() {
        List<CapturedClassInfo> result = new ArrayList<>();
        int[] selected = table.getSelectedRows();
        if (selected.length > 0) {
            for (int viewRow : selected) {
                result.add(model.get(table.convertRowIndexToModel(viewRow)));
            }
        } else {
            for (int viewRow = 0; viewRow < table.getRowCount(); viewRow++) {
                result.add(model.get(table.convertRowIndexToModel(viewRow)));
            }
        }
        return result;
    }

    private void showDetail() {
        int[] selected = table.getSelectedRows();
        if (selected.length == 0) {
            detail.setText("Select a class to see where it was defined.\n\nExport acts on the selected rows, or on every visible row when nothing is selected.");
            detail.setCaretPosition(0);
            return;
        }
        if (selected.length > 1) {
            long total = 0;
            for (int viewRow : selected) {
                total += model.get(table.convertRowIndexToModel(viewRow)).size();
            }
            detail.setText(selected.length + " classes selected, " + formatSize(total)); // idk it would start with Main
            return;
        }
        CapturedClassInfo info = model.get(table.convertRowIndexToModel(selected[0]));
        StringBuilder text = new StringBuilder();
        text.append("Class:    ").append(info.name().replace('/', '.')).append('\n')
                .append("Loader:   ").append(info.loaderName()).append(" (").append(info.loaderType()).append(")\n")
                .append("Loader id: ").append(info.loaderId()).append('\n')
                .append("Origin:   ").append(info.origin() == null ? "none - defined at runtime" : info.origin()).append('\n')
                .append("Source:   ").append(info.source().equals("define") ? "captured while being defined (original bytes)" : "found already loaded (rebuilt by the JVM, may differ slightly from the original bytes)").append('\n')
                .append("Thread:   ").append(info.thread() == null ? "-" : info.thread()).append('\n')
                .append("Size:     ").append(info.size()).append(" bytes\n")
                .append("SHA-256:  ").append(info.sha256()).append('\n')
                .append("Seen:     ").append(info.seen()).append(" time(s)\n");
        if (!info.site().isEmpty()) {
            text.append("\nDefined from:\n");
            for (String frame : info.site()) {
                text.append("    at ").append(frame).append('\n');
            }
        }
        detail.setText(text.toString());
        detail.setCaretPosition(0);
    }

    private void copyNames() {
        List<CapturedClassInfo> infos = selectedOrVisible();
        StringBuilder text = new StringBuilder();
        for (CapturedClassInfo info : infos) {
            text.append(info.name().replace('/', '.')).append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
        statusLabel.setText("Copied " + infos.size() + " class name(s)");
    }

    private void export(boolean asJar) {
        List<CapturedClassInfo> infos = selectedOrVisible();
        if (infos.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no classes to export.", "Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (asJar) {
            chooser.setFileFilter(new FileNameExtensionFilter("Java archive (*.jar)", "jar"));
            chooser.setSelectedFile(new File("dumped-classes.jar"));
        } else {
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        if (asJar && !chosen.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            chosen = new File(chosen.getParentFile(), chosen.getName() + ".jar");
        }
        Path target = chosen.toPath();
        List<Long> ids = new ArrayList<>();
        for (CapturedClassInfo info : infos) {
            ids.add(info.id());
        }
        runRemote("Exporting " + infos.size() + " class(es)...", () -> {
            Map<Long, byte[]> bytes = new HashMap<>(archive.captureFetch(ids));
            return asJar ? DumpExporter.writeJar(target, infos, bytes) : DumpExporter.writeFolder(target, infos, bytes);
        }, result -> {
            DumpExporter.Result exported = (DumpExporter.Result) result;
            StringBuilder message = new StringBuilder("Exported " + exported.written() + " class(es) to\n" + target);
            if (exported.variants() > 0) {
                message.append("\n\n").append(exported.variants())
                        .append(" class(es) share a name with different bytecode and were placed under _variants/.");
            }
            if (exported.identicalSkipped() > 0) {
                message.append("\n").append(exported.identicalSkipped()).append(" identical copy(ies) were skipped.");
            }
            if (exported.missing() > 0) {
                message.append("\n").append(exported.missing()).append(" class(es) were no longer available.");
            }
            message.append("\n\n").append(DumpExporter.INDEX_FILE).append(" lists every entry.");
            JOptionPane.showMessageDialog(this, message.toString(), "Export complete", JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("Exported " + exported.written() + " class(es)");
        });
    }

    private void runRemote(String message, Callable<Object> task, Consumer<Object> onSuccess) {
        if (busy || closed) {
            return;
        }
        busy = true;
        setControlsEnabled(false);
        statusLabel.setText(message);
        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                busy = false;
                if (closed) {
                    return;
                }
                try {
                    onSuccess.accept(get());
                    setControlsEnabled(true);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    statusLabel.setText("Failed: " + messageOf(exception));
                    JOptionPane.showMessageDialog(DumpPanel.this, messageOf(exception), "Dynamic class dump",
                            JOptionPane.ERROR_MESSAGE);
                    setControlsEnabled(true);
                }
            }
        }.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        for (Component component : new Component[]{recordButton, scanButton, clearButton, noCodeSourceBox, lambdaBox,
                limitSpinner, exportJarButton, exportFolderButton, copyButton}) {
            component.setEnabled(enabled);
        }
    }

    private static String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB"};
        double value = bytes;
        int unit = -1;
        do {
            value /= 1024d;
            unit++;
        } while (value >= 1024d && unit < units.length - 1);
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private static final class EntryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "Class", "Loader", "Origin", "Size", "Seen", "Time", "Source", "Thread"};
        private final List<CapturedClassInfo> rows = new ArrayList<>();
        private final Map<Long, Integer> positions = new HashMap<>();
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

        CapturedClassInfo get(int row) {
            return rows.get(row);
        }

        void clear() {
            rows.clear();
            positions.clear();
            fireTableDataChanged();
        }

        void merge(List<CapturedClassInfo> updates) {
            boolean added = false;
            for (CapturedClassInfo info : updates) {
                Integer position = positions.get(info.id());
                if (position != null) {
                    rows.set(position, info);
                    fireTableRowsUpdated(position, position);
                } else {
                    positions.put(info.id(), rows.size());
                    rows.add(info);
                    added = true;
                }
            }
            if (added) {
                fireTableDataChanged();
            }
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
                case 0 -> Long.class;
                case 4, 5 -> Integer.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            CapturedClassInfo info = rows.get(row);
            return switch (column) {
                case 0 -> info.id();
                case 1 -> info.name().replace('/', '.');
                case 2 -> info.loaderName();
                case 3 -> info.origin() == null ? "(runtime)" : info.origin();
                case 4 -> info.size();
                case 5 -> info.seen();
                case 6 -> timeFormat.format(new Date(info.time()));
                case 7 -> info.source();
                default -> info.thread() == null ? "" : info.thread();
            };
        }
    }
}
