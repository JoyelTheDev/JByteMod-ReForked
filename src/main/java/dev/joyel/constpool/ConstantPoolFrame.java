package dev.joyel.constpool;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConstantPoolFrame extends JFrame {

    private static final Color COL_MATCH = new Color(0x1e4d78);

    private final JByteMod jbm;
    private List<ConstantEntry> allEntries = new ArrayList<ConstantEntry>();

    private final ConstantTableModel model = new ConstantTableModel();
    private final JTable table;
    private final TableRowSorter<ConstantTableModel> sorter;

    private final JTextField searchField;
    private final JComboBox<String> kindCombo;
    private final JCheckBox caseCheck;
    private final JLabel statusLabel;
    private final JLabel loadingLabel;
    private final AtomicInteger scanGen = new AtomicInteger();

    public ConstantPoolFrame(JByteMod jbm) {
        super("Constant Pool Viewer");
        this.jbm = jbm;

        table  = buildTable();
        sorter = new TableRowSorter<ConstantTableModel>(model);
        table.setRowSorter(sorter);

        searchField  = new JTextField(22);
        kindCombo    = buildKindCombo();
        caseCheck    = new JCheckBox("Case sensitive", false);
        statusLabel  = new JLabel(" ");
        statusLabel.setForeground(Color.GRAY);
        loadingLabel = new JLabel("Scanning\u2026");
        loadingLabel.setForeground(new Color(0x569cd6));
        loadingLabel.setVisible(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        kindCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { applyFilter(); }
        });
        caseCheck.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { applyFilter(); }
        });

        JButton scanBtn   = toolButton("Scan All",    new Runnable() { public void run() { startScan(); } });
        JButton clearBtn  = toolButton("Clear",       new Runnable() { public void run() { clearResults(); } });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(4, 6, 4, 6));
        toolbar.add(new JLabel("Search: "));
        toolbar.add(searchField);
        toolbar.addSeparator(new Dimension(6, 0));
        toolbar.add(new JLabel("Kind: "));
        toolbar.add(kindCombo);
        toolbar.addSeparator(new Dimension(6, 0));
        toolbar.add(caseCheck);
        toolbar.addSeparator(new Dimension(10, 0));
        toolbar.add(scanBtn);
        toolbar.add(clearBtn);
        toolbar.addSeparator(new Dimension(10, 0));
        toolbar.add(loadingLabel);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(2, 5, 2, 5));
        statusBar.add(statusLabel, BorderLayout.WEST);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel content = new JPanel(new BorderLayout(0, 2));
        content.setBorder(new EmptyBorder(4, 4, 4, 4));
        content.add(toolbar,   BorderLayout.NORTH);
        content.add(scroll,    BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);

        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 620);
        setLocationRelativeTo(jbm);

        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) {
                    searchField.requestFocusInWindow(); searchField.selectAll(); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public static void open(JByteMod jbm) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                ConstantPoolFrame frame = new ConstantPoolFrame(jbm);
                frame.setVisible(true);
                frame.startScan();
            }
        });
    }

    public static void openWithQuery(JByteMod jbm, String query) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                ConstantPoolFrame frame = new ConstantPoolFrame(jbm);
                frame.setVisible(true);
                frame.searchField.setText(query);
                frame.startScan();
            }
        });
    }

    private void startScan() {
        JarArchive archive = jbm.getJarArchive();
        if (archive == null || archive.getClasses() == null) {
            JOptionPane.showMessageDialog(this, "No JAR loaded.",
                    "Constant Pool", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int gen = scanGen.incrementAndGet();
        loadingLabel.setVisible(true);
        statusLabel.setText(" ");
        allEntries.clear();
        model.setRows(allEntries);

        final java.util.Map<String, ClassNode> snapshot =
                new java.util.HashMap<String, ClassNode>(archive.getClasses());

        new SwingWorker<List<ConstantEntry>, Void>() {
            @Override
            protected List<ConstantEntry> doInBackground() {
                return ConstantScanner.scan(snapshot);
            }

            @Override
            protected void done() {
                if (gen != scanGen.get()) return;
                try {
                    allEntries = get();
                } catch (Exception ignored) {
                    allEntries = new ArrayList<ConstantEntry>();
                }
                loadingLabel.setVisible(false);
                applyFilter();
                Main.INSTANCE.getLogger().log(
                        "Constant pool scan: " + allEntries.size() + " constants found.");
            }
        }.execute();
    }

    private void clearResults() {
        scanGen.incrementAndGet();
        allEntries.clear();
        model.setRows(allEntries);
        statusLabel.setText(" ");
        loadingLabel.setVisible(false);
    }

    private void applyFilter() {
        String query   = searchField.getText().trim();
        boolean cs     = caseCheck.isSelected();
        int kindIdx    = kindCombo.getSelectedIndex();
        ConstantKind filterKind = kindIdx == 0 ? null : ConstantKind.values()[kindIdx - 1];

        String queryNorm = cs ? query : query.toLowerCase(java.util.Locale.ROOT);

        List<ConstantEntry> filtered = new ArrayList<ConstantEntry>();
        for (ConstantEntry e : allEntries) {
            if (filterKind != null && e.getKind() != filterKind) continue;
            if (!queryNorm.isEmpty()) {
                String display  = cs ? e.getDisplay()      : e.getDisplay().toLowerCase(java.util.Locale.ROOT);
                String location = cs ? e.getLocationText() : e.getLocationText().toLowerCase(java.util.Locale.ROOT);
                if (!display.contains(queryNorm) && !location.contains(queryNorm)) continue;
            }
            filtered.add(e);
        }
        model.setRows(filtered);
        updateStatus(filtered.size());
    }

    private void updateStatus(int visible) {
        int total = allEntries.size();
        if (total == 0) {
            statusLabel.setText(" No constants loaded. Click Scan All.");
        } else if (visible == total) {
            statusLabel.setText(" " + total + " constants");
        } else {
            statusLabel.setText(" " + visible + " of " + total + " constants");
        }
    }

    private JTable buildTable() {
        JTable t = new JTable(model);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        t.getColumnModel().getColumn(0).setPreferredWidth(110);
        t.getColumnModel().getColumn(0).setMaxWidth(140);
        t.getColumnModel().getColumn(1).setPreferredWidth(420);
        t.getColumnModel().getColumn(2).setPreferredWidth(300);
        t.setRowHeight(20);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFillsViewportHeight(true);

        t.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    navigateToSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = t.rowAtPoint(e.getPoint());
                    if (row >= 0) t.setRowSelectionInterval(row, row);
                    showContextMenu(e.getX(), e.getY());
                }
            }
        });

        t.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) navigateToSelected();
                if (e.getKeyCode() == KeyEvent.VK_C && (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
                    copySelected();
                }
            }
        });

        return t;
    }

    private void showContextMenu(int x, int y) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        ConstantEntry entry = model.getEntry(modelRow);
        if (entry == null) return;

        JPopupMenu menu = new JPopupMenu();

        JMenuItem navigateItem = new JMenuItem("Navigate to Location");
        navigateItem.setFont(navigateItem.getFont().deriveFont(Font.BOLD));
        navigateItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { navigateToSelected(); }
        });
        menu.add(navigateItem);
        menu.addSeparator();

        JMenuItem copyValue = new JMenuItem("Copy Value");
        copyValue.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { copyToClipboard(entry.getDisplay()); }
        });
        menu.add(copyValue);

        JMenuItem copyLocation = new JMenuItem("Copy Location");
        copyLocation.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { copyToClipboard(entry.getLocationText()); }
        });
        menu.add(copyLocation);

        JMenuItem searchThis = new JMenuItem("Filter by This Value");
        searchThis.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                searchField.setText(entry.getDisplay());
            }
        });
        menu.add(searchThis);

        menu.show(table, x, y);
    }

    private void navigateToSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        ConstantEntry entry = model.getEntry(modelRow);
        if (entry == null || entry.getOwnerClass() == null) return;

        ClassNode cn = jbm.getJarArchive().getClasses().get(entry.getOwnerClass().name);
        if (cn == null) return;

        MethodNode mn = null;
        if (entry.getOwnerMethod() != null) {
            for (MethodNode m : cn.methods) {
                if (m.name.equals(entry.getOwnerMethod().name)
                        && m.desc.equals(entry.getOwnerMethod().desc)) {
                    mn = m;
                    break;
                }
            }
        }
        jbm.selectMethod(cn, mn);
    }

    private void copySelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        ConstantEntry entry = model.getEntry(modelRow);
        if (entry != null) copyToClipboard(entry.getDisplay());
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    private JComboBox<String> buildKindCombo() {
        List<String> items = new ArrayList<String>();
        items.add("All");
        for (ConstantKind k : ConstantKind.values()) items.add(k.getLabel());
        return new JComboBox<String>(items.toArray(new String[0]));
    }

    private static JButton toolButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
        return b;
    }
}
