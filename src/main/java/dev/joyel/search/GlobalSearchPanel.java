package dev.joyel.search;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import me.grax.jbytemod.ui.lists.entries.SearchEntry;
import me.grax.jbytemod.utils.list.LazyListModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.Set;

public class GlobalSearchPanel extends JPanel {

    private final JByteMod jbm;

    private final JTextField queryField;
    private final JCheckBox regexBox;
    private final JCheckBox caseBox;

    private final JCheckBox scopeLdcStrings;
    private final JCheckBox scopeLdcNumbers;
    private final JCheckBox scopeClassNames;
    private final JCheckBox scopeMethodNames;
    private final JCheckBox scopeFieldNames;
    private final JCheckBox scopeOpcodes;

    private final JLabel resultCountLabel;
    private final JList<SearchEntry> resultList;
    private final DefaultListModel<SearchEntry> listModel;

    private GlobalSearchTask activeTask;

    public GlobalSearchPanel(JByteMod jbm) {
        this.jbm = jbm;
        setLayout(new BorderLayout(0, 0));

        JPanel topArea = new JPanel(new BorderLayout(4, 4));
        topArea.setBorder(new EmptyBorder(6, 6, 4, 6));

        JPanel queryRow = new JPanel(new BorderLayout(6, 0));
        queryField = new JTextField();
        queryField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        queryField.setToolTipText("Enter search query (plain text or regex)");

        JButton searchBtn = new JButton("Search");
        searchBtn.setFocusPainted(false);
        queryRow.add(queryField, BorderLayout.CENTER);
        queryRow.add(searchBtn, BorderLayout.EAST);

        JPanel optionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        regexBox = new JCheckBox("Regex");
        caseBox = new JCheckBox("Case Sensitive");
        optionRow.add(regexBox);
        optionRow.add(caseBox);

        JPanel scopePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        scopePanel.setBorder(new TitledBorder("Search In"));
        scopeLdcStrings = new JCheckBox("LDC Strings", true);
        scopeLdcNumbers = new JCheckBox("LDC Numbers", false);
        scopeClassNames = new JCheckBox("Class Names", true);
        scopeMethodNames = new JCheckBox("Method Names", true);
        scopeFieldNames = new JCheckBox("Field Names", true);
        scopeOpcodes = new JCheckBox("Opcodes", false);
        scopePanel.add(scopeLdcStrings);
        scopePanel.add(scopeLdcNumbers);
        scopePanel.add(scopeClassNames);
        scopePanel.add(scopeMethodNames);
        scopePanel.add(scopeFieldNames);
        scopePanel.add(scopeOpcodes);

        JPanel configStack = new JPanel();
        configStack.setLayout(new BoxLayout(configStack, BoxLayout.Y_AXIS));
        configStack.add(queryRow);
        configStack.add(Box.createVerticalStrut(4));
        configStack.add(optionRow);
        configStack.add(Box.createVerticalStrut(4));
        configStack.add(scopePanel);

        topArea.add(configStack, BorderLayout.CENTER);

        resultCountLabel = new JLabel("No results");
        resultCountLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultCountLabel.setBorder(new EmptyBorder(2, 6, 2, 6));

        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        resultList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultList.setCellRenderer(new HtmlCellRenderer());

        JScrollPane scrollPane = new JScrollPane(resultList);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(topArea, BorderLayout.NORTH);
        add(resultCountLabel, BorderLayout.CENTER);

        JPanel centerArea = new JPanel(new BorderLayout());
        centerArea.add(resultCountLabel, BorderLayout.NORTH);
        centerArea.add(scrollPane, BorderLayout.CENTER);
        add(topArea, BorderLayout.NORTH);
        add(centerArea, BorderLayout.CENTER);

        searchBtn.addActionListener(e -> triggerSearch());
        queryField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    triggerSearch();
                }
            }
        });

        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigateToSelected();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int idx = resultList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        resultList.setSelectedIndex(idx);
                        showContextMenu(e);
                    }
                }
            }
        });

        resultList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateToSelected();
                }
            }
        });
    }

    private void triggerSearch() {
        String q = queryField.getText().trim();
        if (q.isEmpty()) {
            resultCountLabel.setText("Enter a query to search.");
            return;
        }
        if (jbm.getJarArchive() == null) {
            resultCountLabel.setText("No JAR loaded.");
            return;
        }

        Set<GlobalSearchTask.SearchScope> scopes = EnumSet.noneOf(GlobalSearchTask.SearchScope.class);
        if (scopeLdcStrings.isSelected()) scopes.add(GlobalSearchTask.SearchScope.LDC_STRINGS);
        if (scopeLdcNumbers.isSelected()) scopes.add(GlobalSearchTask.SearchScope.LDC_NUMBERS);
        if (scopeClassNames.isSelected()) scopes.add(GlobalSearchTask.SearchScope.CLASS_NAMES);
        if (scopeMethodNames.isSelected()) scopes.add(GlobalSearchTask.SearchScope.METHOD_NAMES);
        if (scopeFieldNames.isSelected()) scopes.add(GlobalSearchTask.SearchScope.FIELD_NAMES);
        if (scopeOpcodes.isSelected()) scopes.add(GlobalSearchTask.SearchScope.OPCODES);

        if (scopes.isEmpty()) {
            resultCountLabel.setText("Select at least one scope.");
            return;
        }

        if (activeTask != null && !activeTask.isDone()) {
            activeTask.cancel(true);
        }

        listModel.clear();
        resultCountLabel.setText("Searching...");

        activeTask = new GlobalSearchTask(jbm, q, regexBox.isSelected(), caseBox.isSelected(), scopes, this);
        activeTask.execute();
    }

    public void setResults(LazyListModel<SearchEntry> model, int count) {
        listModel.clear();
        for (int i = 0; i < model.getSize(); i++) {
            listModel.addElement(model.getElementAt(i));
        }
        resultCountLabel.setText(count + " result" + (count == 1 ? "" : "s") + " found");
    }

    private void navigateToSelected() {
        SearchEntry entry = resultList.getSelectedValue();
        if (entry == null || entry.getClassNode() == null) return;
        jbm.selectMethod(entry.getClassNode(), entry.getMethodNode());
    }

    private void showContextMenu(MouseEvent e) {
        SearchEntry entry = resultList.getSelectedValue();
        if (entry == null) return;

        JPopupMenu menu = new JPopupMenu();

        JMenuItem goTo = new JMenuItem("Go to Declaration");
        goTo.addActionListener(ev -> navigateToSelected());
        menu.add(goTo);

        JMenuItem selectTree = new JMenuItem("Select in Tree");
        selectTree.addActionListener(ev -> {
            if (entry.getClassNode() != null) {
                jbm.treeSelection(entry.getClassNode(), entry.getMethodNode());
            }
        });
        menu.add(selectTree);

        JMenuItem copy = new JMenuItem("Copy Result Text");
        copy.addActionListener(ev -> {
            String found = entry.getFound();
            if (found != null) {
                StringSelection sel = new StringSelection(found);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            }
        });
        menu.add(copy);

        menu.show(resultList, e.getX(), e.getY());
    }

    public void focusQuery() {
        queryField.requestFocusInWindow();
        queryField.selectAll();
    }

    private static class HtmlCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof SearchEntry) {
                label.setText(((SearchEntry) value).getText());
            }
            label.setBorder(new EmptyBorder(1, 4, 1, 4));
            return label;
        }
    }
}