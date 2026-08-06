package me.grax.jbytemod.ui.xref;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.xref.XrefEntry;
import me.grax.jbytemod.xref.XrefKind;
import me.grax.jbytemod.ui.lists.entries.SearchEntry;
import me.grax.jbytemod.utils.list.LazyListModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class XrefViewerFrame extends JFrame {

    private final JByteMod jbm;
    private final List<XrefEntry> allEntries;
    private final String title;

    private final XrefTableModel tableModel;
    private final JTable table;
    private JTextField searchField;
    private final Map<XrefKind, JCheckBox> kindFilters = new LinkedHashMap<>();
    private JLabel countLabel;

    public XrefViewerFrame(JByteMod jbm, String title, List<XrefEntry> entries) {
        super("Xrefs: " + title);
        this.jbm = jbm;
        this.title = title;
        this.allEntries = new ArrayList<>(entries);

        setSize(780, 500);
        setLocationRelativeTo(jbm);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        setContentPane(root);

        root.add(buildTopBar(), BorderLayout.NORTH);

        tableModel = new XrefTableModel();
        table = new JTable(tableModel);
        configureTable();

        JScrollPane scroll = new JScrollPane(table);
        root.add(scroll, BorderLayout.CENTER);

        countLabel = new JLabel();
        countLabel.setBorder(new EmptyBorder(4, 2, 2, 2));
        root.add(countLabel, BorderLayout.SOUTH);

        applyFilters();

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JPanel buildTopBar() {
        JPanel top = new JPanel(new BorderLayout(4, 4));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Filter...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (XrefKind kind : XrefKind.values()) {
            JCheckBox cb = new JCheckBox(kind.getDisplayName(), true);
            cb.setFont(cb.getFont().deriveFont(11f));
            cb.setForeground(kind.getColor());
            cb.addActionListener(e -> applyFilters());
            kindFilters.put(kind, cb);
            filterPanel.add(cb);
        }

        JButton all = new JButton("All");
        all.setFont(all.getFont().deriveFont(11f));
        all.addActionListener(e -> { kindFilters.values().forEach(c -> c.setSelected(true)); applyFilters(); });
        JButton none = new JButton("None");
        none.setFont(none.getFont().deriveFont(11f));
        none.addActionListener(e -> { kindFilters.values().forEach(c -> c.setSelected(false)); applyFilters(); });
        filterPanel.add(all);
        filterPanel.add(none);

        top.add(new JLabel("Search: "), BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        top.add(filterPanel, BorderLayout.SOUTH);
        return top;
    }

    private void configureTable() {
        table.setFillsViewportHeight(true);
        table.setRowHeight(20);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(16);
        cm.getColumn(0).setMaxWidth(20);
        cm.getColumn(0).setCellRenderer(new KindDotRenderer());
        cm.getColumn(1).setPreferredWidth(80);
        cm.getColumn(1).setMaxWidth(90);
        cm.getColumn(2).setPreferredWidth(100);
        cm.getColumn(2).setMaxWidth(130);
        cm.getColumn(3).setPreferredWidth(200);
        cm.getColumn(4).setPreferredWidth(300);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateToSelected();
                }
            }
        });
    }

    private void navigateToSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int modelRow = table.convertRowIndexToModel(row);
        XrefEntry entry = tableModel.getEntry(modelRow);
        if (!entry.hasNavigationTarget()) return;

        dispose();
        jbm.selectMethod(entry.getOwnerClass(), entry.getOwnerMethod());
        SwingUtilities.invokeLater(() -> {
            jbm.toFront();
        });
    }

    private void applyFilters() {
        String query = searchField.getText().toLowerCase(Locale.ROOT).trim();
        Set<XrefKind> enabled = kindFilters.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<XrefEntry> filtered = allEntries.stream()
                .filter(e -> enabled.contains(e.getKind()))
                .filter(e -> query.isEmpty()
                        || e.getWhereText().toLowerCase().contains(query)
                        || e.getInvocation().toLowerCase().contains(query)
                        || e.getKind().getDisplayName().toLowerCase().contains(query))
                .collect(Collectors.toList());

        tableModel.setEntries(filtered);
        countLabel.setText("  " + filtered.size() + " of " + allEntries.size() + " references");
    }

    private static class XrefTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"", "Access", "Kind", "Opcode / Relation", "Where (Class.Method)"};
        private List<XrefEntry> entries = new ArrayList<>();

        void setEntries(List<XrefEntry> entries) {
            this.entries = new ArrayList<>(entries);
            fireTableDataChanged();
        }

        XrefEntry getEntry(int row) {
            return entries.get(row);
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int col) {
            return COLUMNS[col];
        }

        @Override
        public Object getValueAt(int row, int col) {
            XrefEntry e = entries.get(row);
            if (col == 0) return e.getKind();
            if (col == 1) return e.getAccess().getText();
            if (col == 2) return e.getKind().getDisplayName();
            if (col == 3) return e.getInvocation();
            if (col == 4) return e.getWhereText();
            return "";
        }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? XrefKind.class : String.class;
        }
    }

    private static class KindDotRenderer extends JLabel implements TableCellRenderer {
        KindDotRenderer() {
            setOpaque(true);
            setHorizontalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            if (value instanceof XrefKind) {
                XrefKind kind = (XrefKind) value;
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                setIcon(new KindIcon(kind.getColor()));
                setToolTipText(kind.getDisplayName());
            }
            return this;
        }
    }

    private static class KindIcon implements Icon {
        private final Color color;

        KindIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + 2, 10, 10);
            g2.dispose();
        }

        @Override
        public int getIconWidth() { return 12; }

        @Override
        public int getIconHeight() { return 14; }
    }
}
