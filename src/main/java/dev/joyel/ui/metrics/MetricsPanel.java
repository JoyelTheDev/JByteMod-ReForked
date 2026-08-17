package dev.joyel.ui.metrics;

import de.xbrowniecodez.jbytemod.JByteMod;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MetricsPanel extends JPanel {

    private static final String[] METHOD_COLUMNS = {
        "Method", "Instructions", "Cyclomatic", "Branches", "Handlers", "LDC Strings", "Max Stack", "Max Locals", "Obf Score"
    };

    private final JByteMod jbm;

    private final JLabel classNameLabel;
    private final JLabel[] classSummaryValues;
    private static final String[] CLASS_SUMMARY_LABELS = {
        "Methods", "Fields", "Total Instructions", "Avg Cyclomatic", "Max Cyclomatic", "Exception Handlers", "Obf Score"
    };

    private final ObfScoreBar classObfBar;
    private final DefaultTableModel methodTableModel;
    private final JTable methodTable;
    private final JLabel statusLabel;

    private ClassNode currentClass;

    public MetricsPanel(JByteMod jbm) {
        this.jbm = jbm;
        setLayout(new BorderLayout(0, 4));
        setBorder(new EmptyBorder(6, 6, 6, 6));

        classNameLabel = new JLabel("No class selected");
        classNameLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        classNameLabel.setBorder(new EmptyBorder(0, 0, 4, 0));

        classSummaryValues = new JLabel[CLASS_SUMMARY_LABELS.length];
        JPanel summaryPanel = buildSummaryPanel();

        classObfBar = new ObfScoreBar();
        classObfBar.setBorder(new TitledBorder("Class Obfuscation Score"));

        JPanel topArea = new JPanel(new BorderLayout(0, 6));
        topArea.add(classNameLabel, BorderLayout.NORTH);
        topArea.add(summaryPanel, BorderLayout.CENTER);
        topArea.add(classObfBar, BorderLayout.SOUTH);

        methodTableModel = new DefaultTableModel(METHOD_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
            @Override
            public Class<?> getColumnClass(int col) {
                return col == 0 ? String.class : Integer.class;
            }
        };
        methodTable = new JTable(methodTableModel);
        methodTable.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        methodTable.getTableHeader().setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        methodTable.setAutoCreateRowSorter(true);
        methodTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        methodTable.setRowHeight(20);
        methodTable.setFillsViewportHeight(true);
        methodTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        for (int i = 1; i < METHOD_COLUMNS.length; i++) {
            methodTable.getColumnModel().getColumn(i).setPreferredWidth(90);
        }

        methodTable.setDefaultRenderer(Integer.class, new MetricCellRenderer());
        methodTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = methodTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        methodTable.setRowSelectionInterval(row, row);
                        showContextMenu(e, row);
                    }
                } else if (e.getClickCount() == 2) {
                    navigateToSelectedMethod();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(methodTable);
        tableScroll.setBorder(new TitledBorder("Method Breakdown"));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        statusLabel.setForeground(Color.GRAY);

        add(topArea, BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 4, 8, 4));
        panel.setBorder(new TitledBorder("Class Summary"));
        for (int i = 0; i < CLASS_SUMMARY_LABELS.length; i++) {
            JLabel key = new JLabel(CLASS_SUMMARY_LABELS[i] + ":");
            key.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            classSummaryValues[i] = new JLabel("-");
            classSummaryValues[i].setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            panel.add(key);
            panel.add(classSummaryValues[i]);
        }
        return panel;
    }

    public void showClassMetrics(ClassNode cn) {
        this.currentClass = cn;
        BytecodeMetrics.ClassMetrics cm = BytecodeMetrics.forClass(cn);

        classNameLabel.setText(cn.name.replace('/', '.'));

        int[] values = {
            cm.methodCount(), cm.fieldCount(), cm.totalInstructions(),
            cm.avgCyclomaticComplexity(), cm.maxCyclomaticComplexity(),
            cm.totalExceptionHandlers(), cm.obfuscationScore()
        };
        for (int i = 0; i < classSummaryValues.length; i++) {
            classSummaryValues[i].setText(String.valueOf(values[i]));
            if (i == CLASS_SUMMARY_LABELS.length - 1) {
                classSummaryValues[i].setForeground(scoreColor(values[i]));
            } else {
                classSummaryValues[i].setForeground(UIManager.getColor("Label.foreground"));
            }
        }

        classObfBar.setScore(cm.obfuscationScore());

        methodTableModel.setRowCount(0);
        List<MethodNode> methods = new ArrayList<>(cn.methods);
        for (MethodNode mn : methods) {
            BytecodeMetrics.MethodMetrics mm = BytecodeMetrics.forMethod(mn);
            methodTableModel.addRow(new Object[]{
                mn.name + mn.desc,
                mm.instructionCount(),
                mm.cyclomaticComplexity(),
                mm.branchCount(),
                mm.exceptionHandlers(),
                mm.ldcStrings(),
                mm.maxStack(),
                mm.maxLocals(),
                mm.obfuscationScore()
            });
        }

        statusLabel.setText(cn.methods.size() + " methods  |  " + (cn.fields == null ? 0 : cn.fields.size()) + " fields  |  double-click row to navigate");
    }

    public void showMethodMetrics(ClassNode cn, MethodNode mn) {
        showClassMetrics(cn);
        String target = mn.name + mn.desc;
        for (int i = 0; i < methodTableModel.getRowCount(); i++) {
            if (target.equals(methodTableModel.getValueAt(i, 0))) {
                int viewRow = methodTable.convertRowIndexToView(i);
                methodTable.setRowSelectionInterval(viewRow, viewRow);
                methodTable.scrollRectToVisible(methodTable.getCellRect(viewRow, 0, true));
                break;
            }
        }
    }

    private void navigateToSelectedMethod() {
        int viewRow = methodTable.getSelectedRow();
        if (viewRow < 0 || currentClass == null) return;
        int modelRow = methodTable.convertRowIndexToModel(viewRow);
        String sig = (String) methodTableModel.getValueAt(modelRow, 0);
        for (MethodNode mn : currentClass.methods) {
            if ((mn.name + mn.desc).equals(sig)) {
                jbm.selectMethod(currentClass, mn);
                return;
            }
        }
    }

    private void showContextMenu(MouseEvent e, int viewRow) {
        int modelRow = methodTable.convertRowIndexToModel(viewRow);
        String sig = (String) methodTableModel.getValueAt(modelRow, 0);

        JPopupMenu menu = new JPopupMenu();

        JMenuItem goTo = new JMenuItem("Go to Method");
        goTo.addActionListener(ev -> navigateToSelectedMethod());
        menu.add(goTo);

        JMenuItem copy = new JMenuItem("Copy Row as Text");
        copy.addActionListener(ev -> {
            StringBuilder sb = new StringBuilder(sig);
            for (int col = 1; col < METHOD_COLUMNS.length; col++) {
                sb.append("\t").append(methodTableModel.getValueAt(modelRow, col));
            }
            StringSelection sel = new StringSelection(sb.toString());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
        });
        menu.add(copy);

        JMenuItem copyAll = new JMenuItem("Copy All Rows as TSV");
        copyAll.addActionListener(ev -> copyTableAsTsv());
        menu.add(copyAll);

        menu.show(methodTable, e.getX(), e.getY());
    }

    private void copyTableAsTsv() {
        StringBuilder sb = new StringBuilder(String.join("\t", METHOD_COLUMNS)).append("\n");
        for (int row = 0; row < methodTableModel.getRowCount(); row++) {
            for (int col = 0; col < METHOD_COLUMNS.length; col++) {
                if (col > 0) sb.append("\t");
                sb.append(methodTableModel.getValueAt(row, col));
            }
            sb.append("\n");
        }
        StringSelection sel = new StringSelection(sb.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
    }

    private static Color scoreColor(int score) {
        if (score >= 70) return new Color(0xff5555);
        if (score >= 40) return new Color(0xffaa33);
        return new Color(0x55cc88);
    }

    private static class ObfScoreBar extends JPanel {
        private int score = 0;

        ObfScoreBar() {
            setPreferredSize(new Dimension(0, 28));
        }

        void setScore(int score) {
            this.score = score;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 20;
            int h = 14;
            int x = 10;
            int y = (getHeight() - h) / 2;
            g2.setColor(new Color(0x444444));
            g2.fillRoundRect(x, y, w, h, 6, 6);
            int filled = (int) (w * score / 100.0);
            Color barColor = score >= 70 ? new Color(0xff5555) : score >= 40 ? new Color(0xffaa33) : new Color(0x55cc88);
            g2.setColor(barColor);
            g2.fillRoundRect(x, y, Math.max(6, filled), h, 6, 6);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            String label = score + "%";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h - 2);
            g2.dispose();
        }
    }

    private static class MetricCellRenderer extends DefaultTableCellRenderer {
        private static final int[] HIGH_THRESHOLDS = {500, 20, 15, 10, 0, 0, 0, 70};
        private static final int[] WARN_THRESHOLDS = {200, 10, 8,  5,  0, 0, 0, 40};

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);
            if (value instanceof Integer && column > 0 && !isSelected) {
                int v = (Integer) value;
                int col = column - 1;
                if (col < HIGH_THRESHOLDS.length && HIGH_THRESHOLDS[col] > 0) {
                    if (v >= HIGH_THRESHOLDS[col]) setForeground(new Color(0xff5555));
                    else if (v >= WARN_THRESHOLDS[col]) setForeground(new Color(0xffaa33));
                    else setForeground(UIManager.getColor("Table.foreground"));
                } else {
                    setForeground(UIManager.getColor("Table.foreground"));
                }
            }
            return this;
        }
    }
}