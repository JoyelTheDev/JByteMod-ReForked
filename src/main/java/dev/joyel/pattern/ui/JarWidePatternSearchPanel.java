package dev.joyel.pattern.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.pattern.*;
import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

public final class JarWidePatternSearchPanel extends JPanel {

    private static final long SWING_TIMER_MS = 30;
    private static final int MAX_RESULTS = 2000;
    private static final Color COLOR_VALID  = new Color(0x2e7d32);
    private static final Color COLOR_ERROR  = new Color(0xc62828);
    private static final Color COLOR_WARN   = new Color(0xe65100);

    private final JByteMod jbm;

    private final JTextArea patternArea;
    private final JCheckBox includeMetadataBox;
    private final JLabel diagnosticLabel;
    private final JButton searchButton;
    private final JButton cancelButton;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final JLabel matchCountLabel;

    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode treeRoot;
    private final JTree resultTree;

    private InstructionPatternCompiler.Compilation compilation;
    private PatternSearchSession session;
    private javax.swing.Timer advanceTimer;
    private boolean resultsPopulated;

    public JarWidePatternSearchPanel(JByteMod jbm) {
        this.jbm = jbm;
        this.compilation = InstructionPatternCompiler.compile("", false);

        setLayout(new BorderLayout(0, 0));

        patternArea = new JTextArea(6, 40);
        patternArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        patternArea.setTabSize(4);
        patternArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { onPatternChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { onPatternChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onPatternChanged(); }
        });
        patternArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "search");
        patternArea.getActionMap().put("search", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { triggerSearch(); }
        });

        includeMetadataBox = new JCheckBox("Include metadata (labels, frames, line numbers)");
        includeMetadataBox.addActionListener(e -> onPatternChanged());

        diagnosticLabel = new JLabel(" ");
        diagnosticLabel.setFont(diagnosticLabel.getFont().deriveFont(Font.PLAIN, 11f));

        searchButton = new JButton("Search all methods");
        searchButton.setEnabled(false);
        searchButton.addActionListener(e -> triggerSearch());

        cancelButton = new JButton("Cancel");
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> cancelSearch());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);

        matchCountLabel = new JLabel(" ");
        matchCountLabel.setFont(matchCountLabel.getFont().deriveFont(Font.BOLD, 12f));

        treeRoot = new DefaultMutableTreeNode("Results");
        treeModel = new DefaultTreeModel(treeRoot);
        resultTree = new JTree(treeModel);
        resultTree.setRootVisible(false);
        resultTree.setShowsRootHandles(true);
        resultTree.setCellRenderer(new ResultTreeRenderer());
        resultTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        resultTree.addTreeSelectionListener(this::onTreeSelection);
        resultTree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) navigateToSelected();
            }
        });
        resultTree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "navigate");
        resultTree.getActionMap().put("navigate", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { navigateToSelected(); }
        });

        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildResultsPanel(), BorderLayout.CENTER);
    }

    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBorder(new EmptyBorder(6, 6, 0, 6));

        JPanel editorPanel = new JPanel(new BorderLayout(0, 2));

        JPanel editorHeader = new JPanel(new BorderLayout());
        JLabel editorLabel = new JLabel("Pattern  (Ctrl+Enter to search)");
        editorLabel.setFont(editorLabel.getFont().deriveFont(Font.BOLD, 12f));
        editorHeader.add(editorLabel, BorderLayout.WEST);
        JButton helpBtn = new JButton("?");
        helpBtn.setMargin(new Insets(1, 5, 1, 5));
        helpBtn.setFont(helpBtn.getFont().deriveFont(Font.BOLD, 11f));
        helpBtn.addActionListener(e -> showHelp());
        editorHeader.add(helpBtn, BorderLayout.EAST);
        editorPanel.add(editorHeader, BorderLayout.NORTH);

        JScrollPane patternScroll = new JScrollPane(patternArea);
        patternScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        editorPanel.add(patternScroll, BorderLayout.CENTER);

        JPanel diagRow = new JPanel(new BorderLayout());
        diagRow.add(diagnosticLabel, BorderLayout.CENTER);
        diagRow.add(includeMetadataBox, BorderLayout.EAST);
        editorPanel.add(diagRow, BorderLayout.SOUTH);

        JPanel controls = new JPanel(new BorderLayout(4, 4));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(searchButton);
        buttons.add(cancelButton);
        controls.add(buttons, BorderLayout.WEST);
        controls.add(matchCountLabel, BorderLayout.EAST);

        JPanel progressRow = new JPanel(new BorderLayout(6, 0));
        progressRow.add(progressBar, BorderLayout.CENTER);
        progressRow.add(statusLabel, BorderLayout.EAST);

        JPanel bottomRow = new JPanel(new BorderLayout(0, 2));
        bottomRow.add(controls, BorderLayout.NORTH);
        bottomRow.add(progressRow, BorderLayout.SOUTH);
        bottomRow.setBorder(new EmptyBorder(4, 0, 4, 0));

        top.add(editorPanel, BorderLayout.CENTER);
        top.add(bottomRow, BorderLayout.SOUTH);
        return top;
    }

    private JPanel buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(0, 6, 6, 6));

        JLabel resultsHeader = new JLabel("Results");
        resultsHeader.setFont(resultsHeader.getFont().deriveFont(Font.BOLD, 12f));
        resultsHeader.setBorder(new EmptyBorder(4, 0, 2, 0));

        JToolBar treeToolbar = new JToolBar();
        treeToolbar.setFloatable(false);
        JButton expandAll = new JButton("Expand all");
        JButton collapseAll = new JButton("Collapse all");
        expandAll.setFont(expandAll.getFont().deriveFont(11f));
        collapseAll.setFont(collapseAll.getFont().deriveFont(11f));
        expandAll.addActionListener(e -> expandAll());
        collapseAll.addActionListener(e -> collapseAll());
        treeToolbar.add(resultsHeader);
        treeToolbar.add(Box.createHorizontalStrut(8));
        treeToolbar.add(expandAll);
        treeToolbar.add(collapseAll);

        JScrollPane treeScroll = new JScrollPane(resultTree);
        panel.add(treeToolbar, BorderLayout.NORTH);
        panel.add(treeScroll, BorderLayout.CENTER);
        return panel;
    }

    private void onPatternChanged() {
        String text = patternArea.getText();
        compilation = InstructionPatternCompiler.compile(text, includeMetadataBox.isSelected());
        updateDiagnostic();
        searchButton.setEnabled(compilation.valid() && session == null);
        if (session != null) {
            session.cancel();
            session = null;
            stopTimer();
            cancelButton.setVisible(false);
            progressBar.setVisible(false);
            searchButton.setEnabled(compilation.valid());
        }
        resultsPopulated = false;
    }

    private void updateDiagnostic() {
        PatternDiagnostic d = compilation.primaryDiagnostic();
        if (d == null) {
            diagnosticLabel.setText("Pattern valid");
            diagnosticLabel.setForeground(COLOR_VALID);
        } else {
            Color c = d.severity() == PatternDiagnostic.Severity.ERROR ? COLOR_ERROR : COLOR_WARN;
            diagnosticLabel.setText("Line " + d.line() + ", col " + d.column() + ": " + d.message());
            diagnosticLabel.setForeground(c);
        }
    }

    private void triggerSearch() {
        if (!compilation.valid()) return;
        JarArchive jar = jbm.getJarArchive();
        if (jar == null || jar.getClasses() == null || jar.getClasses().isEmpty()) {
            statusLabel.setText("No JAR loaded.");
            return;
        }
        clearResults();
        resultsPopulated = false;
        session = new PatternSearchSession(jar, compilation.pattern(), MAX_RESULTS);
        searchButton.setEnabled(false);
        cancelButton.setVisible(true);
        progressBar.setVisible(true);
        progressBar.setValue(0);
        progressBar.setString("0 / " + session.methodCount() + " methods");
        statusLabel.setText("Searching...");
        matchCountLabel.setText(" ");

        advanceTimer = new javax.swing.Timer((int) SWING_TIMER_MS, e -> advanceSearch());
        advanceTimer.start();
    }

    private void advanceSearch() {
        if (session == null) return;
        session.advance(10_000_000L);

        int pct = (int) (session.progress() * 100);
        progressBar.setValue(pct);
        progressBar.setString(session.methodsSearched() + " / " + session.methodCount() + " methods");

        if (session.isFinished()) {
            stopTimer();
            if (!session.isCancelled() && !resultsPopulated) {
                resultsPopulated = true;
                populateTree(session.results(), session.matchCount());
            }
            session = null;
            searchButton.setEnabled(compilation.valid());
            cancelButton.setVisible(false);
            progressBar.setVisible(false);
        }
    }

    private void populateTree(List<InstructionPatternMatch> matches, long totalCount) {
        clearResults();

        Map<String, DefaultMutableTreeNode> classNodes = new LinkedHashMap<>();
        Map<String, DefaultMutableTreeNode> methodNodes = new LinkedHashMap<>();

        for (InstructionPatternMatch match : matches) {
            String className = match.getOwnerClass().name;
            String methodKey = className + "#" + match.getMethod().name + match.getMethod().desc;

            DefaultMutableTreeNode classNode = classNodes.get(className);
            if (classNode == null) {
                classNode = new DefaultMutableTreeNode(new ClassResultNode(match.getOwnerClass()));
                classNodes.put(className, classNode);
                treeRoot.add(classNode);
            }

            DefaultMutableTreeNode methodNode = methodNodes.get(methodKey);
            if (methodNode == null) {
                methodNode = new DefaultMutableTreeNode(new MethodResultNode(match.getOwnerClass(), match.getMethod()));
                methodNodes.put(methodKey, methodNode);
                classNode.add(methodNode);
            }

            methodNode.add(new DefaultMutableTreeNode(new MatchResultNode(match)));
        }

        updateMatchCounts(classNodes, methodNodes);

        treeModel.reload();
        expandFirstLevel();

        long shown = matches.size();
        if (totalCount > shown) {
            statusLabel.setText("Showing first " + shown + " of " + totalCount + " matches (limit reached)");
            statusLabel.setForeground(COLOR_WARN);
        } else if (totalCount == 0) {
            statusLabel.setText("No matches found.");
            statusLabel.setForeground(COLOR_WARN);
        } else {
            statusLabel.setText("Done.");
            statusLabel.setForeground(COLOR_VALID);
        }

        String countText = totalCount + (totalCount == 1 ? " match" : " matches") +
                " in " + classNodes.size() + (classNodes.size() == 1 ? " class" : " classes") +
                ", " + methodNodes.size() + (methodNodes.size() == 1 ? " method" : " methods");
        matchCountLabel.setText(countText);
        matchCountLabel.setForeground(totalCount == 0 ? COLOR_WARN : COLOR_VALID);
    }

    private void updateMatchCounts(Map<String, DefaultMutableTreeNode> classNodes,
                                   Map<String, DefaultMutableTreeNode> methodNodes) {
        for (DefaultMutableTreeNode methodNode : methodNodes.values()) {
            Object userObj = methodNode.getUserObject();
            if (userObj instanceof MethodResultNode) {
                ((MethodResultNode) userObj).matchCount = methodNode.getChildCount();
            }
        }
        for (DefaultMutableTreeNode classNode : classNodes.values()) {
            Object userObj = classNode.getUserObject();
            if (userObj instanceof ClassResultNode) {
                int total = 0;
                for (int i = 0; i < classNode.getChildCount(); i++) {
                    DefaultMutableTreeNode child = (DefaultMutableTreeNode) classNode.getChildAt(i);
                    if (child.getUserObject() instanceof MethodResultNode) {
                        total += ((MethodResultNode) child.getUserObject()).matchCount;
                    }
                }
                ((ClassResultNode) userObj).matchCount = total;
            }
        }
    }

    private void clearResults() {
        treeRoot.removeAllChildren();
        treeModel.reload();
        matchCountLabel.setText(" ");
        statusLabel.setText(" ");
    }

    private void onTreeSelection(TreeSelectionEvent e) {
        TreePath path = e.getNewLeadSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (userObj instanceof MatchResultNode) {
            InstructionPatternMatch match = ((MatchResultNode) userObj).match;
            navigateTo(match, false);
        }
    }

    private void navigateToSelected() {
        TreePath path = resultTree.getSelectionPath();
        if (path == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        Object userObj = node.getUserObject();
        if (userObj instanceof MatchResultNode) {
            navigateTo(((MatchResultNode) userObj).match, true);
        } else if (userObj instanceof MethodResultNode) {
            MethodResultNode mn = (MethodResultNode) userObj;
            jbm.selectMethod(mn.classNode, mn.methodNode);
        } else if (userObj instanceof ClassResultNode) {
            jbm.selectClass(((ClassResultNode) userObj).classNode);
        }
    }

    private void navigateTo(InstructionPatternMatch match, boolean focusCodeList) {
        jbm.selectMethod(match.getOwnerClass(), match.getMethod());
        AbstractInsnNode target = match.getNavigationInstruction();
        if (target != null) {
            int idx = 0;
            for (AbstractInsnNode insn : match.getMethod().instructions) {
                if (insn == target) break;
                idx++;
            }
            int finalIdx = idx;
            SwingUtilities.invokeLater(() -> {
                jbm.getCodeList().setSelectedIndex(finalIdx);
                jbm.getCodeList().ensureIndexIsVisible(finalIdx);
                if (focusCodeList) jbm.getCodeList().requestFocusInWindow();
            });
        }
    }

    private void cancelSearch() {
        if (session != null) {
            session.cancel();
            session = null;
        }
        stopTimer();
        cancelButton.setVisible(false);
        progressBar.setVisible(false);
        searchButton.setEnabled(compilation.valid());
        statusLabel.setText("Cancelled.");
        statusLabel.setForeground(Color.GRAY);
    }

    private void stopTimer() {
        if (advanceTimer != null) { advanceTimer.stop(); advanceTimer = null; }
    }

    private void expandFirstLevel() {
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            resultTree.expandPath(new TreePath(((DefaultMutableTreeNode) treeRoot.getChildAt(i)).getPath()));
        }
    }

    private void expandAll() {
        for (int i = 0; i < resultTree.getRowCount(); i++) {
            resultTree.expandRow(i);
        }
    }

    private void collapseAll() {
        for (int i = resultTree.getRowCount() - 1; i >= 0; i--) {
            resultTree.collapseRow(i);
        }
    }

    public void setPattern(String text) {
        patternArea.setText(text);
    }

    private void showHelp() {
        String help =
            "Pattern Syntax\n" +
            "==============\n\n" +
            "Write instructions in assembler format, one per line.\n\n" +
            "Wildcards\n" +
            "---------\n" +
            "  *           Any single instruction\n" +
            "  ...         Any sequence of zero or more instructions\n" +
            "  *           As an operand: any value for that operand\n\n" +
            "Glob in quoted strings\n" +
            "----------------------\n" +
            "  \"java/*\"    Any class starting with java/\n" +
            "  \"get*\"      Any method name starting with get\n\n" +
            "Examples\n" +
            "--------\n" +
            "  # Any ldc followed by println\n" +
            "  ldc *\n" +
            "  invokevirtual \"java/io/PrintStream\" \"println\" * *\n\n" +
            "  # Any arithmetic with a gap\n" +
            "  iload *\n" +
            "  ...\n" +
            "  iadd\n" +
            "  ireturn\n\n" +
            "Keyboard shortcut\n" +
            "-----------------\n" +
            "  Ctrl+Enter  Run search\n";
        JTextArea ta = new JTextArea(help);
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JOptionPane.showMessageDialog(jbm, new JScrollPane(ta), "Pattern Syntax Help", JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class ClassResultNode {
        final ClassNode classNode;
        int matchCount;

        ClassResultNode(ClassNode classNode) {
            this.classNode = classNode;
        }

        @Override
        public String toString() {
            String name = classNode.name;
            int slash = name.lastIndexOf('/');
            String simple = slash >= 0 ? name.substring(slash + 1) : name;
            return simple + "  (" + matchCount + (matchCount == 1 ? " match" : " matches") + ")";
        }
    }

    private static final class MethodResultNode {
        final ClassNode classNode;
        final MethodNode methodNode;
        int matchCount;

        MethodResultNode(ClassNode classNode, MethodNode methodNode) {
            this.classNode = classNode;
            this.methodNode = methodNode;
        }

        @Override
        public String toString() {
            return methodNode.name + methodNode.desc +
                    "  (" + matchCount + (matchCount == 1 ? " match" : " matches") + ")";
        }
    }

    private static final class MatchResultNode {
        final InstructionPatternMatch match;

        MatchResultNode(InstructionPatternMatch match) {
            this.match = match;
        }

        @Override
        public String toString() {
            String fmt = match.getFormattedInstructions();
            int nl = fmt.indexOf('\n');
            String first = nl >= 0 ? fmt.substring(0, nl) : fmt;
            int extra = match.getInstructions().size() - 1;
            return extra > 0 ? first + "  (+" + extra + " more)" : first;
        }
    }

    private static final class ResultTreeRenderer extends DefaultTreeCellRenderer {

        private static final Color CLASS_FG   = new Color(0x1a5276);
        private static final Color METHOD_FG  = new Color(0x1e8449);
        private static final Color MATCH_FG   = new Color(0x4a4a4a);

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof ClassResultNode) {
                    setFont(getFont().deriveFont(Font.BOLD, 12f));
                    if (!selected) setForeground(CLASS_FG);
                    setIcon(null);
                } else if (userObj instanceof MethodResultNode) {
                    setFont(getFont().deriveFont(Font.PLAIN, 12f));
                    if (!selected) setForeground(METHOD_FG);
                    setIcon(null);
                } else if (userObj instanceof MatchResultNode) {
                    setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
                    if (!selected) setForeground(MATCH_FG);
                    setIcon(null);
                    MatchResultNode mn = (MatchResultNode) userObj;
                    String fmt = mn.match.getFormattedInstructions();
                    setToolTipText("<html><pre style='font-size:11px'>" + escHtml(fmt) + "</pre></html>");
                }
            }
            return this;
        }

        private static String escHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
