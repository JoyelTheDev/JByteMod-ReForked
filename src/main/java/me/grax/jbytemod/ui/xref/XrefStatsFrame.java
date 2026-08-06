package me.grax.jbytemod.ui.xref;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.xref.MemberKey;
import me.grax.jbytemod.xref.XrefEntry;
import me.grax.jbytemod.xref.XrefMap;
import me.grax.jbytemod.xref.XrefManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class XrefStatsFrame extends JFrame {

    private final JByteMod jbm;
    private final JTable table;
    private final StatsTableModel tableModel;
    private final JTextField searchField;
    private final JComboBox<String> modeBox;
    private final JLabel countLabel;

    private static final String MODE_MEMBERS = "Member References";
    private static final String MODE_CLASSES = "Class References";

    public XrefStatsFrame(JByteMod jbm) {
        super("Xref Statistics");
        this.jbm = jbm;
        setSize(860, 560);
        setLocationRelativeTo(jbm);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        setContentPane(root);

        JPanel top = new JPanel(new BorderLayout(4, 4));
        modeBox = new JComboBox<>(new String[]{MODE_MEMBERS, MODE_CLASSES});
        modeBox.addActionListener(e -> refresh());
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Filter by name...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        });
        JButton rebuildBtn = new JButton("Rebuild Index");
        rebuildBtn.addActionListener(e -> {
            if (jbm.getJarArchive() == null) return;
            rebuildBtn.setEnabled(false);
            rebuildBtn.setText("Building...");
            XrefManager.getInstance().buildAsync(jbm.getJarArchive(), () -> {
                rebuildBtn.setText("Rebuild Index");
                rebuildBtn.setEnabled(true);
                refresh();
            });
        });

        top.add(modeBox, BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);
        top.add(rebuildBtn, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        tableModel = new StatsTableModel();
        table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(19);
        table.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(400);
        cm.getColumn(1).setPreferredWidth(120);
        cm.getColumn(1).setMaxWidth(160);
        cm.getColumn(2).setPreferredWidth(300);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) openXrefForSelected();
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) openXrefForSelected();
            }
        });

        root.add(new JScrollPane(table), BorderLayout.CENTER);

        countLabel = new JLabel();
        countLabel.setBorder(new EmptyBorder(3, 2, 2, 2));
        root.add(countLabel, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        refresh();
    }

    private void refresh() {
        if (!XrefManager.getInstance().isReady()) {
            tableModel.setRows(Collections.emptyList());
            countLabel.setText("  Xref index not built. Load a JAR and the index will build automatically.");
            return;
        }

        String query = searchField.getText().toLowerCase(Locale.ROOT).trim();
        boolean memberMode = MODE_MEMBERS.equals(modeBox.getSelectedItem());
        XrefMap map = XrefManager.getInstance().getCurrentMap();

        List<StatRow> rows;
        if (memberMode) {
            rows = map.getAllMemberRefs().entrySet().stream()
                    .filter(e -> query.isEmpty()
                            || e.getKey().toString().toLowerCase().contains(query))
                    .sorted(Comparator.comparingInt((Map.Entry<MemberKey, List<XrefEntry>> e) ->
                            e.getValue().size()).reversed())
                    .map(e -> new StatRow(
                            e.getKey().toString(),
                            e.getValue().size(),
                            describeKinds(e.getValue())))
                    .collect(Collectors.toList());
        } else {
            rows = map.getAllClassRefs().entrySet().stream()
                    .filter(e -> query.isEmpty()
                            || e.getKey().toLowerCase().contains(query))
                    .sorted(Comparator.comparingInt((Map.Entry<String, List<XrefEntry>> e) ->
                            e.getValue().size()).reversed())
                    .map(e -> new StatRow(
                            e.getKey(),
                            e.getValue().size(),
                            describeKinds(e.getValue())))
                    .collect(Collectors.toList());
        }

        tableModel.setRows(rows);
        countLabel.setText("  " + rows.size() + " entries");
    }

    private void openXrefForSelected() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        int modelRow = table.convertRowIndexToModel(row);
        StatRow sr = tableModel.getRow(modelRow);

        boolean memberMode = MODE_MEMBERS.equals(modeBox.getSelectedItem());
        XrefMap map = XrefManager.getInstance().getCurrentMap();
        List<XrefEntry> entries;

        if (memberMode) {
            String[] parts = sr.key.split("\\.");
            if (parts.length < 2) return;
            String owner = parts[0];
            String rest = sr.key.substring(owner.length() + 1);
            int parenIdx = rest.indexOf('(');
            String name = parenIdx == -1 ? rest : rest.substring(0, parenIdx);
            String desc = parenIdx == -1 ? "" : rest.substring(parenIdx);
            entries = map.getMemberRefs(owner, name, desc);
        } else {
            entries = map.getClassRefs(sr.key);
        }

        if (entries.isEmpty()) return;
        new XrefViewerFrame(jbm, sr.key, entries).setVisible(true);
    }

    private static String describeKinds(List<XrefEntry> entries) {
        Map<String, Long> counts = entries.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getKind().getDisplayName(), Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> e.getKey() + " ×" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static final class StatRow {
        final String key;
        final int count;
        final String kindSummary;

        StatRow(String key, int count, String kindSummary) {
            this.key = key;
            this.count = count;
            this.kindSummary = kindSummary;
        }
    }

    private static class StatsTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Member / Class", "Ref Count", "Kind Breakdown"};
        private List<StatRow> rows = new ArrayList<>();

        void setRows(List<StatRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        StatRow getRow(int i) {
            return rows.get(i);
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int col) { return COLS[col]; }
        @Override public Class<?> getColumnClass(int col) {
            return col == 1 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int col) {
            StatRow r = rows.get(row);
            if (col == 0) return r.key;
            if (col == 1) return r.count;
            if (col == 2) return r.kindSummary;
            return "";
        }
    }
}
