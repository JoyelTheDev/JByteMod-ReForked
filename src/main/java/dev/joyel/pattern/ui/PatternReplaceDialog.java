package dev.joyel.pattern.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.pattern.*;
import me.grax.jbytemod.JarArchive;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

public final class PatternReplaceDialog extends JDialog {
    private static final long SWING_TIMER_MS = 30;
    private static final Color COLOR_VALID = new Color(0x2e7d32);
    private static final Color COLOR_ERROR = new Color(0xc62828);
    private static final Color COLOR_WARN = new Color(0xe65100);

    private final JByteMod jbm;
    private final JTextArea searchArea;
    private final JTextArea replaceArea;
    private final JLabel diagnosticLabel;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JButton replaceButton;
    private final JButton cancelButton;
    private final JCheckBox includeMetadata;

    private InstructionPatternCompiler.Compilation compilation;
    private PatternReplaceSession session;
    private Timer advanceTimer;

    public PatternReplaceDialog(JByteMod jbm) {
        super(jbm, "Bytecode Search & Replace", true);
        this.jbm = jbm;
        this.compilation = InstructionPatternCompiler.compile("", false);

        searchArea = new JTextArea(7, 50);
        searchArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        searchArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { onSearchChanged(); }
        });

        replaceArea = new JTextArea(7, 50);
        replaceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        diagnosticLabel = new JLabel(" ");
        diagnosticLabel.setFont(diagnosticLabel.getFont().deriveFont(Font.PLAIN, 12f));

        includeMetadata = new JCheckBox("Include Metadata");
        includeMetadata.addActionListener(e -> onSearchChanged());

        replaceButton = new JButton("Replace All");
        replaceButton.setEnabled(false);
        replaceButton.addActionListener(e -> startReplace());

        cancelButton = new JButton("Cancel");
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> cancelReplace());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);

        buildLayout();
        pack();
        setMinimumSize(new Dimension(680, 560));
        setLocationRelativeTo(jbm);
    }

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout(6, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(new TitledBorder("Search Pattern"));
        searchPanel.add(new JScrollPane(searchArea), BorderLayout.CENTER);

        JPanel replacePanel = new JPanel(new BorderLayout());
        replacePanel.setBorder(new TitledBorder("Replacement Instructions"));
        replacePanel.add(new JScrollPane(replaceArea), BorderLayout.CENTER);

        JLabel replaceHint = new JLabel("<html><font color='#888888'>Leave replacement empty to delete matched instructions. Use the same assembler syntax as the search pattern.</font></html>");
        replaceHint.setBorder(new EmptyBorder(2, 4, 4, 4));
        replacePanel.add(replaceHint, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, searchPanel, replacePanel);
        split.setResizeWeight(0.5);
        split.setDividerSize(6);

        JPanel diagRow = new JPanel(new BorderLayout(4, 0));
        diagRow.setBorder(new EmptyBorder(2, 0, 0, 0));
        diagRow.add(diagnosticLabel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.add(replaceButton);
        controls.add(cancelButton);
        controls.add(new JSeparator(SwingConstants.VERTICAL));
        controls.add(includeMetadata);

        JPanel progressRow = new JPanel(new BorderLayout(6, 0));
        progressRow.add(progressBar, BorderLayout.CENTER);
        progressRow.add(statusLabel, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.add(diagRow, BorderLayout.NORTH);
        bottom.add(controls, BorderLayout.CENTER);
        bottom.add(progressRow, BorderLayout.SOUTH);

        root.add(split, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
    }

    public void setSearchText(String text) {
        searchArea.setText(text);
    }

    private void onSearchChanged() {
        compilation = InstructionPatternCompiler.compile(searchArea.getText(), includeMetadata.isSelected());
        PatternDiagnostic d = compilation.primaryDiagnostic();
        if (d == null) {
            diagnosticLabel.setText("Search pattern valid – ready to replace");
            diagnosticLabel.setForeground(COLOR_VALID);
        } else {
            Color c = d.severity() == PatternDiagnostic.Severity.ERROR ? COLOR_ERROR : COLOR_WARN;
            diagnosticLabel.setText("Line " + d.line() + ", col " + d.column() + ": " + d.message());
            diagnosticLabel.setForeground(c);
        }
        replaceButton.setEnabled(compilation.valid() && session == null);
        cancelReplace();
    }

    private void startReplace() {
        if (!compilation.valid()) return;
        JarArchive jar = jbm.getJarArchive();
        if (jar == null || jar.getClasses() == null || jar.getClasses().isEmpty()) {
            statusLabel.setText("No JAR loaded.");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "This will permanently modify the loaded bytecode in memory.\nProceed with Replace All?",
                "Confirm Replace All", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        session = new PatternReplaceSession(jar, compilation.pattern(), replaceArea.getText());
        replaceButton.setEnabled(false);
        cancelButton.setVisible(true);
        progressBar.setValue(0);
        progressBar.setVisible(true);
        statusLabel.setText("Replacing...");
        statusLabel.setForeground(Color.GRAY);

        advanceTimer = new Timer((int) SWING_TIMER_MS, e -> advanceReplace());
        advanceTimer.start();
    }

    private void advanceReplace() {
        if (session == null) return;
        session.advance(10_000_000L);
        int pct = (int) (session.progress() * 100);
        progressBar.setValue(pct);
        progressBar.setString(session.methodsProcessed() + " / " + session.methodCount() + " methods");

        if (session.isFinished()) {
            stopTimer();
            onReplaceFinished(session);
            session = null;
            replaceButton.setEnabled(compilation.valid());
            cancelButton.setVisible(false);
        }
    }

    private void onReplaceFinished(PatternReplaceSession done) {
        progressBar.setVisible(false);
        int replaced = done.totalReplacements();
        int methods = done.methodsModified();
        if (!done.errors().isEmpty()) {
            StringBuilder sb = new StringBuilder("Errors in " + done.errors().size() + " method(s):\n");
            done.errors().forEach(err -> sb.append("  ").append(err).append("\n"));
            JOptionPane.showMessageDialog(this, sb.toString(), "Replace Errors", JOptionPane.WARNING_MESSAGE);
        }
        if (replaced > 0) {
            statusLabel.setText("Replaced " + replaced + " match(es) across " + methods + " method(s).");
            statusLabel.setForeground(COLOR_VALID);
            jbm.getCodeList().revalidate();
            jbm.getCodeList().repaint();
        } else {
            statusLabel.setText("No matches found.");
            statusLabel.setForeground(COLOR_WARN);
        }
    }

    private void cancelReplace() {
        if (session != null) session = null;
        stopTimer();
        replaceButton.setEnabled(compilation.valid());
        cancelButton.setVisible(false);
        progressBar.setVisible(false);
    }

    private void stopTimer() {
        if (advanceTimer != null) { advanceTimer.stop(); advanceTimer = null; }
    }
}
