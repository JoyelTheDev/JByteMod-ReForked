package dev.joyel.pattern.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.pattern.*;
import me.grax.jbytemod.JarArchive;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.List;

public final class PatternSearchDialog extends JDialog {
    private static final long SWING_TIMER_MS = 30;
    private static final int MAX_RESULTS = 500;
    private static final Color COLOR_VALID = new Color(0x2e7d32);
    private static final Color COLOR_ERROR = new Color(0xc62828);
    private static final Color COLOR_WARN = new Color(0xe65100);
    private static final Color COLOR_STALE = new Color(0x757575);

    private final JByteMod jbm;
    private final JTextArea patternArea;
    private final JLabel diagnosticLabel;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JButton searchButton;
    private final JButton cancelButton;
    private final JButton searchReplaceButton;
    private final JCheckBox includeMetadata;
    private final DefaultListModel<InstructionPatternMatch> resultModel = new DefaultListModel<>();
    private final JList<InstructionPatternMatch> resultList;

    private InstructionPatternCompiler.Compilation compilation;
    private PatternSearchSession session;
    private Timer advanceTimer;
    private boolean resultsShown;

    public PatternSearchDialog(JByteMod jbm) {
        super(jbm, "Pattern Search", false);
        this.jbm = jbm;
        this.compilation = InstructionPatternCompiler.compile("", false);

        patternArea = new JTextArea(8, 50);
        patternArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        patternArea.setTabSize(4);
        patternArea.setText("");
        patternArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onPatternChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onPatternChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onPatternChanged(); }
        });
        patternArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "search");
        patternArea.getActionMap().put("search", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { triggerSearch(); }
        });

        diagnosticLabel = new JLabel(" ");
        diagnosticLabel.setFont(diagnosticLabel.getFont().deriveFont(Font.PLAIN, 12f));

        includeMetadata = new JCheckBox("Include Metadata (labels, frames, line numbers)");
        includeMetadata.addActionListener(e -> onPatternChanged());

        searchButton = new JButton("Search");
        searchButton.setEnabled(false);
        searchButton.addActionListener(e -> triggerSearch());

        cancelButton = new JButton("Cancel");
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> cancelSearch("Cancelled"));

        searchReplaceButton = new JButton("Search & Replace...");
        searchReplaceButton.addActionListener(e -> openReplace());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);

        resultList = new JList<>(resultModel);
        resultList.setCellRenderer(new PatternMatchRenderer());
        resultList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) navigateToSelected();
            }
        });
        resultList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "navigate");
        resultList.getActionMap().put("navigate", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { navigateToSelected(); }
        });

        buildLayout();
        pack();
        setMinimumSize(new Dimension(680, 520));
        setLocationRelativeTo(jbm);
    }

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new BorderLayout(4, 4));
        JPanel patternPanel = new JPanel(new BorderLayout());
        patternPanel.setBorder(new TitledBorder("Search Pattern  (Ctrl+Enter to search)"));
        JScrollPane patternScroll = new JScrollPane(patternArea);
        patternScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        patternPanel.add(patternScroll, BorderLayout.CENTER);

        JPanel hintPanel = new JPanel(new BorderLayout());
        JLabel hint = new JLabel("<html><font color='#888888'>Wildcards: <b>*</b> matches any single instruction or operand &nbsp;&nbsp; <b>...</b> matches any sequence of instructions</font></html>");
        hint.setBorder(new EmptyBorder(2, 4, 2, 4));
        hintPanel.add(hint, BorderLayout.WEST);
        patternPanel.add(hintPanel, BorderLayout.SOUTH);
        top.add(patternPanel, BorderLayout.CENTER);

        JPanel diagPanel = new JPanel(new BorderLayout(4, 0));
        diagPanel.setBorder(new EmptyBorder(2, 0, 0, 0));
        diagPanel.add(diagnosticLabel, BorderLayout.CENTER);
        top.add(diagPanel, BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.add(searchButton);
        controls.add(cancelButton);
        controls.add(searchReplaceButton);
        controls.add(new JSeparator(SwingConstants.VERTICAL));
        controls.add(includeMetadata);

        JPanel progressPanel = new JPanel(new BorderLayout(6, 0));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.EAST);

        JPanel bottomControls = new JPanel(new BorderLayout());
        bottomControls.add(controls, BorderLayout.NORTH);
        bottomControls.add(progressPanel, BorderLayout.CENTER);

        JPanel resultsPanel = new JPanel(new BorderLayout(4, 4));
        resultsPanel.setBorder(new TitledBorder("Results"));
        JScrollPane resultScroll = new JScrollPane(resultList);
        resultScroll.setPreferredSize(new Dimension(0, 200));
        resultsPanel.add(resultScroll, BorderLayout.CENTER);

        root.add(top, BorderLayout.NORTH);
        root.add(bottomControls, BorderLayout.CENTER);
        root.add(resultsPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void onPatternChanged() {
        String text = patternArea.getText();
        compilation = InstructionPatternCompiler.compile(text, includeMetadata.isSelected());
        updateDiagnostic();
        searchButton.setEnabled(compilation.valid() && session == null);
        cancelSearch(null);
        resultsShown = false;
    }

    private void updateDiagnostic() {
        PatternDiagnostic d = compilation.primaryDiagnostic();
        if (d == null) {
            diagnosticLabel.setText("Pattern is valid");
            diagnosticLabel.setForeground(COLOR_VALID);
        } else {
            Color c = d.severity() == PatternDiagnostic.Severity.ERROR ? COLOR_ERROR : COLOR_WARN;
            diagnosticLabel.setText("Line " + d.line() + ", column " + d.column() + ": " + d.message());
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
        resultModel.clear();
        resultsShown = false;
        session = new PatternSearchSession(jar, compilation.pattern(), MAX_RESULTS);
        searchButton.setEnabled(false);
        cancelButton.setVisible(true);
        progressBar.setVisible(true);
        progressBar.setValue(0);
        statusLabel.setText("Searching...");

        advanceTimer = new Timer((int) SWING_TIMER_MS, e -> advanceSearch());
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
            if (!session.isCancelled() && !resultsShown) {
                resultsShown = true;
                showResults(session.results(), session.matchCount());
            }
            session = null;
            searchButton.setEnabled(compilation.valid());
            cancelButton.setVisible(false);
        }
    }

    private void showResults(List<InstructionPatternMatch> matches, long totalCount) {
        resultModel.clear();
        for (InstructionPatternMatch m : matches) resultModel.addElement(m);
        long shown = matches.size();
        if (totalCount > shown) {
            statusLabel.setText(totalCount + " matches found; showing first " + shown);
        } else {
            statusLabel.setText(totalCount + (totalCount == 1 ? " match found" : " matches found"));
        }
        statusLabel.setForeground(totalCount == 0 ? COLOR_WARN : COLOR_VALID);
        progressBar.setVisible(false);
    }

    private void cancelSearch(String message) {
        if (session != null) session.cancel();
        session = null;
        stopTimer();
        searchButton.setEnabled(compilation.valid());
        cancelButton.setVisible(false);
        progressBar.setVisible(false);
        if (message != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(COLOR_STALE);
        }
    }

    private void stopTimer() {
        if (advanceTimer != null) { advanceTimer.stop(); advanceTimer = null; }
    }

    private void navigateToSelected() {
        InstructionPatternMatch match = resultList.getSelectedValue();
        if (match == null) return;
        jbm.setCurrentNode(match.getOwnerClass());
        jbm.getCodeList().loadInstructions(match.getMethod());
        if (match.getNavigationInstruction() != null) {
            int idx = 0;
            for (AbstractInsnNode insn : match.getMethod().instructions) {
                if (insn == match.getNavigationInstruction()) break;
                idx++;
            }
            jbm.getCodeList().setSelectedIndex(idx);
            jbm.getCodeList().ensureIndexIsVisible(idx);
        }
    }

    private void openReplace() {
        PatternReplaceDialog dlg = new PatternReplaceDialog(jbm);
        dlg.setSearchText(patternArea.getText());
        dlg.setVisible(true);
    }

    private static final class PatternMatchRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
            if (value instanceof InstructionPatternMatch) {
                InstructionPatternMatch m = (InstructionPatternMatch) value;
                String formatted = m.getFormattedInstructions();
                int nl = formatted.indexOf('\n');
                String first = nl >= 0 ? formatted.substring(0, nl) : formatted;
                String extra = m.getInstructions().size() > 1 ? "  +" + (m.getInstructions().size() - 1) + " more" : "";
                setText("<html><b>" + escHtml(first) + "</b>" + escHtml(extra)
                        + "&nbsp;&nbsp;<font color='#888888'>" + escHtml(m.getMethodDisplayName()) + "</font></html>");
                setToolTipText("<html><pre>" + escHtml(formatted) + "</pre></html>");
            }
            return this;
        }

        private static String escHtml(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
