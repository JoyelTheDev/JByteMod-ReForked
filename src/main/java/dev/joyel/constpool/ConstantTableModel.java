package dev.joyel.constpool;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

final class ConstantTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Kind", "Value", "Location"};

    private List<ConstantEntry> rows = new ArrayList<ConstantEntry>();

    void setRows(List<ConstantEntry> rows) {
        this.rows = new ArrayList<ConstantEntry>(rows);
        fireTableDataChanged();
    }

    ConstantEntry getEntry(int row) {
        if (row < 0 || row >= rows.size()) return null;
        return rows.get(row);
    }

    @Override 
    public int getRowCount()    { return rows.size(); }
    @Override 
    public int getColumnCount() { return COLUMNS.length; }
    @Override 
    public String getColumnName(int col) { return COLUMNS[col]; }

    @Override
    public Object getValueAt(int row, int col) {
        ConstantEntry e = rows.get(row);
        switch (col) {
            case 0: return e.getKind().getLabel();
            case 1: return e.getDisplay();
            case 2: return e.getLocationText();
            default: return "";
        }
    }

    @Override
    public Class<?> getColumnClass(int col) { return String.class; }
}
