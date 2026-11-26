package dev.joyel.plugins;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class PluginManagerPanel extends JPanel {
    private final PluginHost host;
    private final PluginRepositoryService repository;
    private final Supplier<String> applicationVersion;
    private final InstalledModel installedModel = new InstalledModel();
    private final BrowseModel browseModel = new BrowseModel();
    private final JTable installedTable = new JTable(installedModel);
    private final JTable browseTable = new JTable(browseModel);
    private final TableRowSorter<BrowseModel> browseSorter = new TableRowSorter<>(browseModel);
    private final JTextArea installedDetail = new JTextArea();
    private final JTextArea browseDetail = new JTextArea();
    private final JTextField searchField = new JTextField(22);
    private final JButton installFileButton = new JButton("Install from file...");
    private final JButton removeButton = new JButton("Remove");
    private final JButton folderButton = new JButton("Open plugins folder");
    private final JButton reloadButton = new JButton("Reload all");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton repositoriesButton = new JButton("Repositories...");
    private final JButton installButton = new JButton("Install");
    private final JButton websiteButton = new JButton("Open website");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel(" ");
    private final JTabbedPane tabs = new JTabbedPane();
    private List<String> catalogMessages = List.of();
    private boolean busy;
    private boolean catalogLoaded;

    public PluginManagerPanel(PluginHost host, PluginRepositoryService repository, Supplier<String> applicationVersion) {
        this.host = host;
        this.repository = repository;
        this.applicationVersion = applicationVersion;
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        installedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedTable.setFillsViewportHeight(true);
        int[] installedWidths = {70, 260, 80, 140, 170, 170};
        for (int index = 0; index < installedWidths.length; index++) {
            installedTable.getColumnModel().getColumn(index).setPreferredWidth(installedWidths[index]);
        }
        browseTable.setRowSorter(browseSorter);
        browseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        browseTable.setFillsViewportHeight(true);
        int[] browseWidths = {260, 80, 140, 200, 240};
        for (int index = 0; index < browseWidths.length; index++) {
            browseTable.getColumnModel().getColumn(index).setPreferredWidth(browseWidths[index]);
        }
        for (JTextArea area : new JTextArea[]{installedDetail, browseDetail}) {
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        }

        JPanel installedActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        installedActions.add(installFileButton);
        installedActions.add(removeButton);
        installedActions.add(folderButton);
        installedActions.add(reloadButton);
        JPanel installedPage = new JPanel(new BorderLayout(0, 6));
        JSplitPane installedSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(installedTable), new JScrollPane(installedDetail));
        installedSplit.setResizeWeight(0.65);
        installedPage.add(installedSplit, BorderLayout.CENTER);
        installedPage.add(installedActions, BorderLayout.SOUTH);

        JPanel browseTop = new JPanel(new BorderLayout(8, 0));
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        search.add(new JLabel("Search:"));
        search.add(searchField);
        browseTop.add(search, BorderLayout.WEST);
        JPanel browseButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        browseButtons.add(refreshButton);
        browseButtons.add(repositoriesButton);
        browseTop.add(browseButtons, BorderLayout.EAST);
        JPanel browseActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        browseActions.add(installButton);
        browseActions.add(websiteButton);
        JPanel browsePage = new JPanel(new BorderLayout(0, 6));
        browsePage.add(browseTop, BorderLayout.NORTH);
        JSplitPane browseSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(browseTable), new JScrollPane(browseDetail));
        browseSplit.setResizeWeight(0.65);
        browsePage.add(browseSplit, BorderLayout.CENTER);
        browsePage.add(browseActions, BorderLayout.SOUTH);

        tabs.addTab("Installed", installedPage);
        tabs.addTab("Browse", browsePage);
        add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(statusLabel, BorderLayout.CENTER);
        progress.setPreferredSize(new java.awt.Dimension(180, 14));
        progress.setVisible(false);
        bottom.add(progress, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        installFileButton.addActionListener(event -> installFromFile());
        removeButton.addActionListener(event -> removeSelected());
        folderButton.addActionListener(event -> openFolder());
        reloadButton.addActionListener(event -> reloadAll());
        refreshButton.addActionListener(event -> loadCatalog());
        repositoriesButton.addActionListener(event -> editRepositories());
        installButton.addActionListener(event -> installSelectedFromRepository());
        websiteButton.addActionListener(event -> openWebsite());
        installedTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showInstalledDetail();
                updateButtons();
            }
        });
        browseTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showBrowseDetail();
                updateButtons();
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applySearch();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applySearch();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applySearch();
            }
        });
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedIndex() == 1 && !catalogLoaded) {
                loadCatalog();
            }
            updateButtons();
        });
        refreshInstalled();
        showInstalledDetail();
        showBrowseDetail();
        updateButtons();
    }

    public void selectBrowseTab() {
        tabs.setSelectedIndex(1);
    }

    public void refreshInstalled() {
        int selected = installedTable.getSelectedRow();
        String selectedKey = selected < 0 ? null : installedModel.get(selected).key();
        installedModel.set(host.records());
        if (selectedKey != null) {
            for (int row = 0; row < installedModel.getRowCount(); row++) {
                if (installedModel.get(row).key().equals(selectedKey)) {
                    installedTable.setRowSelectionInterval(row, row);
                    break;
                }
            }
        }
        browseModel.fireTableDataChanged();
        showInstalledDetail();
        updateButtons();
    }

    private PluginRecord selectedInstalled() {
        int row = installedTable.getSelectedRow();
        return row < 0 ? null : installedModel.get(installedTable.convertRowIndexToModel(row));
    }

    private PluginRepositoryService.RepositoryPlugin selectedEntry() {
        int row = browseTable.getSelectedRow();
        return row < 0 ? null : browseModel.get(browseTable.convertRowIndexToModel(row));
    }

    private PluginRecord installedFor(PluginRepositoryService.RepositoryPlugin entry) {
        for (PluginRecord record : host.records()) {
            if (!record.isBuiltin() && record.matches(entry.id())) {
                return record;
            }
        }
        return null;
    }

    private String entryStatus(PluginRepositoryService.RepositoryPlugin entry) {
        PluginRecord installed = installedFor(entry);
        if (!entry.compatibleWith(applicationVersion.get())) {
            return "Requires JByteMod " + entry.minimumJByteModVersion();
        }
        if (installed != null) {
            if (!installed.version().isEmpty() && VersionCompare.isNewer(entry.version(), installed.version())) {
                return "Update available (installed " + installed.version() + ")";
            }
            return "Installed" + (installed.version().isEmpty() ? "" : " (" + installed.version() + ")");
        }
        if (!entry.downloadable()) {
            return "No verified download";
        }
        return "Not installed";
    }

    private void showInstalledDetail() {
        PluginRecord record = selectedInstalled();
        if (record == null) {
            installedDetail.setText("Select a plugin to see its details. Use the checkbox to enable or disable it without restarting.");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(record.name()).append(record.version().isEmpty() ? "" : "  v" + record.version()).append('\n');
        if (!record.author().isEmpty()) {
            text.append("Author:  ").append(record.author()).append('\n');
        }
        text.append("Source:  ").append(record.isBuiltin() ? "Built in" : record.file().toString()).append('\n');
        text.append("Status:  ").append(record.state().label()).append('\n');
        if (!record.description().isEmpty()) {
            text.append('\n').append(record.description()).append('\n');
        }
        if (record.error() != null) {
            text.append("\nProblem: ").append(record.error()).append('\n')
                    .append("The plugin was not started. Disabling it has no effect; remove it or install a fixed version.\n");
        }
        installedDetail.setText(text.toString());
        installedDetail.setCaretPosition(0);
    }

    private void showBrowseDetail() {
        PluginRepositoryService.RepositoryPlugin entry = selectedEntry();
        if (entry == null) {
            StringBuilder text = new StringBuilder();
            if (!catalogLoaded) {
                text.append("Open this tab to load the plugin repositories.");
            } else if (browseModel.getRowCount() == 0) {
                text.append("No plugins are available from the configured repositories.");
            } else {
                text.append("Select a plugin to see its details.");
            }
            for (String message : catalogMessages) {
                text.append("\n- ").append(message);
            }
            browseDetail.setText(text.toString());
            browseDetail.setCaretPosition(0);
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append(entry.name()).append("  v").append(entry.version()).append('\n')
                .append("Author:      ").append(entry.author()).append('\n')
                .append("Repository:  ").append(entry.repositoryName()).append('\n')
                .append("Status:      ").append(entryStatus(entry)).append('\n');
        if (!entry.minimumJByteModVersion().isEmpty()) {
            text.append("Requires:    JByteMod ").append(entry.minimumJByteModVersion()).append(" or newer\n");
        }
        if (!entry.website().isEmpty()) {
            text.append("Website:     ").append(entry.website()).append('\n');
        }
        if (entry.downloadable()) {
            text.append("Download:    ").append(entry.downloadUrl()).append('\n')
                    .append("SHA-256:     ").append(entry.sha256()).append('\n');
        }
        if (!entry.description().isEmpty()) {
            text.append('\n').append(entry.description()).append('\n');
        }
        browseDetail.setText(text.toString());
        browseDetail.setCaretPosition(0);
    }

    private void updateButtons() {
        boolean idle = !busy;
        PluginRecord installed = selectedInstalled();
        installFileButton.setEnabled(idle);
        removeButton.setEnabled(idle && installed != null && !installed.isBuiltin());
        folderButton.setEnabled(idle);
        reloadButton.setEnabled(idle);
        refreshButton.setEnabled(idle);
        repositoriesButton.setEnabled(idle);
        PluginRepositoryService.RepositoryPlugin entry = selectedEntry();
        boolean usable = idle && entry != null && entry.downloadable() && entry.compatibleWith(applicationVersion.get());
        installButton.setEnabled(usable);
        String status = entry == null ? "" : entryStatus(entry);
        installButton.setText(status.startsWith("Update") ? "Update" : status.startsWith("Installed") ? "Reinstall" : "Install");
        websiteButton.setEnabled(entry != null && !entry.website().isEmpty());
    }

    private void applySearch() {
        String text = searchField.getText().trim().toLowerCase(Locale.ROOT);
        browseSorter.setRowFilter(text.isEmpty() ? null : new RowFilter<BrowseModel, Integer>() {
            @Override
            public boolean include(Entry<? extends BrowseModel, ? extends Integer> row) {
                PluginRepositoryService.RepositoryPlugin plugin = row.getModel().get(row.getIdentifier());
                return plugin.name().toLowerCase(Locale.ROOT).contains(text)
                        || plugin.id().toLowerCase(Locale.ROOT).contains(text)
                        || plugin.author().toLowerCase(Locale.ROOT).contains(text)
                        || plugin.description().toLowerCase(Locale.ROOT).contains(text);
            }
        });
    }

    private void loadCatalog() {
        if (busy) {
            return;
        }
        busy = true;
        statusLabel.setText("Loading plugin repositories...");
        progress.setIndeterminate(true);
        progress.setVisible(true);
        updateButtons();
        new SwingWorker<PluginRepositoryService.Catalog, Void>() {
            @Override
            protected PluginRepositoryService.Catalog doInBackground() {
                return repository.loadCatalog();
            }

            @Override
            protected void done() {
                busy = false;
                progress.setIndeterminate(false);
                progress.setVisible(false);
                try {
                    PluginRepositoryService.Catalog catalog = get();
                    catalogLoaded = true;
                    catalogMessages = catalog.messages();
                    browseModel.set(catalog.plugins());
                    statusLabel.setText(catalog.plugins().size() + " plugin(s) available"
                            + (catalog.messages().isEmpty() ? "" : " - " + catalog.messages().size() + " note(s)"));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    statusLabel.setText("Could not load repositories: " + messageOf(exception));
                }
                showBrowseDetail();
                updateButtons();
            }
        }.execute();
    }

    private void installSelectedFromRepository() {
        PluginRepositoryService.RepositoryPlugin entry = selectedEntry();
        if (entry == null || busy) {
            return;
        }
        PluginRecord existing = installedFor(entry);
        String action = existing == null ? "Install" : "Update";
        int choice = JOptionPane.showConfirmDialog(this,
                action + " " + entry.name() + " " + entry.version() + " from " + entry.repositoryName() + "?\n\n"
                        + "Plugins run with the same access as JByteMod itself, including your files and network.\n"
                        + "Only continue if you trust the author and the repository.\n\n"
                        + "The download is verified against the SHA-256 listed in the repository:\n" + entry.sha256(),
                action + " plugin", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        busy = true;
        progress.setValue(0);
        progress.setVisible(true);
        statusLabel.setText("Downloading " + entry.name() + "...");
        updateButtons();
        new SwingWorker<Path, Integer>() {
            @Override
            protected Path doInBackground() throws Exception {
                return repository.downloadPlugin(entry, this::publish);
            }

            @Override
            protected void process(List<Integer> chunks) {
                progress.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                Path downloaded = null;
                try {
                    downloaded = get();
                    PluginRecord record = host.install(downloaded, entry.fileName().isEmpty() ? entry.id() + ".jar" : entry.fileName(), entry.id());
                    statusLabel.setText((existing == null ? "Installed " : "Updated ") + record.name() + " " + record.version());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    statusLabel.setText("Download failed");
                    error(messageOf(exception));
                } catch (Exception failure) {
                    statusLabel.setText("Install failed");
                    error(failure.getMessage() == null ? failure.toString() : failure.getMessage());
                } finally {
                    repository.deleteDownload(downloaded);
                    busy = false;
                    progress.setVisible(false);
                    refreshInstalled();
                    showBrowseDetail();
                }
            }
        }.execute();
    }

    private void installFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select a plugin JAR");
        chooser.setFileFilter(new FileNameExtensionFilter("Plugin JAR (*.jar)", "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            PluginHost.InspectResult info = host.inspect(file.toPath());
            int choice = JOptionPane.showConfirmDialog(this,
                    "Install " + file.getName() + "?\n\nPlugin class(es):\n  " + String.join("\n  ", info.pluginClasses())
                            + "\n\nPlugins run with the same access as JByteMod itself. Only install plugins you trust.",
                    "Install plugin", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            PluginRecord record = host.install(file.toPath(), file.getName(), null);
            statusLabel.setText("Installed " + record.name() + " " + record.version());
        } catch (Exception failure) {
            statusLabel.setText("Install failed");
            error(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }
        refreshInstalled();
    }

    private void removeSelected() {
        PluginRecord record = selectedInstalled();
        if (record == null || record.isBuiltin()) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "Remove " + record.name() + " and delete " + record.fileName() + "?",
                "Remove plugin", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            host.remove(record);
            statusLabel.setText("Removed " + record.name());
        } catch (Exception failure) {
            error(failure.getMessage() == null ? failure.toString() : failure.getMessage());
        }
        refreshInstalled();
    }

    private void reloadAll() {
        host.reload();
        statusLabel.setText("Plugins reloaded");
        refreshInstalled();
    }

    private void openFolder() {
        try {
            Path folder = host.folder();
            Files.createDirectories(folder);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder.toFile());
            } else {
                statusLabel.setText(folder.toString());
            }
        } catch (Exception failure) {
            error("Could not open the folder: " + failure.getMessage());
        }
    }

    private void openWebsite() {
        PluginRepositoryService.RepositoryPlugin entry = selectedEntry();
        if (entry == null || entry.website().isEmpty()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(entry.website()));
            } else {
                statusLabel.setText(entry.website());
            }
        } catch (Exception failure) {
            error("Could not open the website: " + failure.getMessage());
        }
    }

    private void editRepositories() {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Plugin repositories", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        DefaultListModel<PluginRepositoryService.RepositorySource> model = new DefaultListModel<>();
        repository.sources().forEach(model::addElement);
        JList<PluginRepositoryService.RepositorySource> list = new JList<>(model);
        list.setCellRenderer((view, value, index, selected, focused) -> {
            JLabel label = new JLabel(value.displayName() + (value.builtinDefault() ? "  (default)" : ""));
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            label.setBackground(selected ? view.getSelectionBackground() : view.getBackground());
            label.setForeground(selected ? view.getSelectionForeground() : view.getForeground());
            return label;
        });
        JTextField input = new JTextField(36);
        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove selected");
        JLabel note = new JLabel("<html>Use an https:// address, a GitHub repository, or a local folder containing plugins.json.</html>");
        Runnable addAction = () -> {
            try {
                repository.addSource(input.getText());
                model.clear();
                repository.sources().forEach(model::addElement);
                input.setText("");
            } catch (IllegalArgumentException invalid) {
                JOptionPane.showMessageDialog(dialog, invalid.getMessage(), "Cannot add repository", JOptionPane.ERROR_MESSAGE);
            }
        };
        add.addActionListener(event -> addAction.run());
        input.addActionListener(event -> addAction.run());
        remove.addActionListener(event -> {
            PluginRepositoryService.RepositorySource selected = list.getSelectedValue();
            if (selected == null) {
                return;
            }
            try {
                repository.removeSource(selected);
                model.removeElement(selected);
            } catch (IllegalArgumentException invalid) {
                JOptionPane.showMessageDialog(dialog, invalid.getMessage(), "Cannot remove repository", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.add(input, BorderLayout.CENTER);
        inputRow.add(add, BorderLayout.EAST);
        JPanel south = new JPanel(new GridLayout(0, 1, 0, 6));
        south.add(note);
        south.add(inputRow);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        close.addActionListener(event -> dialog.dispose());
        buttons.add(remove);
        buttons.add(close);
        south.add(buttons);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(new JScrollPane(list), BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(640, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        catalogLoaded = false;
        loadCatalog();
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Plugin manager", JOptionPane.ERROR_MESSAGE);
    }

    private static String messageOf(ExecutionException exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private final class InstalledModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Enabled", "Name", "Version", "Author", "Source", "Status"};
        private List<PluginRecord> rows = new ArrayList<>();

        void set(List<PluginRecord> records) {
            rows = new ArrayList<>(records);
            fireTableDataChanged();
        }

        PluginRecord get(int row) {
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
            return column == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && !busy;
        }

        @Override
        public Object getValueAt(int row, int column) {
            PluginRecord record = rows.get(row);
            return switch (column) {
                case 0 -> record.isEnabled();
                case 1 -> record.name();
                case 2 -> record.version();
                case 3 -> record.author();
                case 4 -> record.isBuiltin() ? "Built in" : record.fileName();
                default -> record.state() == PluginRecord.State.FAILED
                        ? "Failed - " + record.error() : record.state().label();
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column != 0 || !(value instanceof Boolean enabled)) {
                return;
            }
            PluginRecord record = rows.get(row);
            host.setEnabled(record, enabled);
            statusLabel.setText(record.name() + (enabled ? " enabled" : " disabled"));
            SwingUtilities.invokeLater(PluginManagerPanel.this::refreshInstalled);
        }
    }

    private final class BrowseModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Name", "Version", "Author", "Repository", "Status"};
        private List<PluginRepositoryService.RepositoryPlugin> rows = List.of();

        void set(List<PluginRepositoryService.RepositoryPlugin> plugins) {
            rows = plugins;
            fireTableDataChanged();
        }

        PluginRepositoryService.RepositoryPlugin get(int row) {
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
            PluginRepositoryService.RepositoryPlugin plugin = rows.get(row);
            return switch (column) {
                case 0 -> plugin.name();
                case 1 -> plugin.version();
                case 2 -> plugin.author();
                case 3 -> plugin.repositoryName();
                default -> entryStatus(plugin);
            };
        }
    }
}
