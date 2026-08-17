package dev.joyel.ui.script;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.objectweb.asm.tree.ClassNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

public class ScriptConsolePanel extends JPanel {

    private static final int MAX_HISTORY = 50;

    private final JByteMod jbm;
    private final List<JbmScriptEngine> engines;

    private JComboBox<String> engineSelector;
    private RSyntaxTextArea editorArea;
    private JTextPane outputPane;
    private StyledDocument outputDoc;
    private SimpleAttributeSet normalStyle;
    private SimpleAttributeSet errorStyle;
    private SimpleAttributeSet resultStyle;
    private SimpleAttributeSet dimStyle;

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    public ScriptConsolePanel(JByteMod jbm) {
        this.jbm = jbm;
        this.engines = ScriptEngineFactory.availableEngines();
        setLayout(new BorderLayout(0, 0));
        initStyles();
        buildUI();
    }

    private void initStyles() {
        normalStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(normalStyle, new Color(0xc0c0c0));
        StyleConstants.setFontFamily(normalStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(normalStyle, 12);

        errorStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(errorStyle, new Color(0xff6060));
        StyleConstants.setFontFamily(errorStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(errorStyle, 12);

        resultStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(resultStyle, new Color(0x55cc88));
        StyleConstants.setFontFamily(resultStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(resultStyle, 12);

        dimStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(dimStyle, new Color(0x888888));
        StyleConstants.setFontFamily(dimStyle, Font.MONOSPACED);
        StyleConstants.setFontSize(dimStyle, 12);
    }

    private void buildUI() {
        JPanel toolbar = buildToolbar();
        add(toolbar, BorderLayout.NORTH);

        editorArea = buildEditor();
        RTextScrollPane editorScroll = new RTextScrollPane(editorArea);
        editorScroll.setLineNumbersEnabled(true);

        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setBackground(new Color(0x1e1e1e));
        outputPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputDoc = outputPane.getStyledDocument();
        JScrollPane outputScroll = new JScrollPane(outputPane);
        outputScroll.setBorder(new TitledBorder("Output"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, outputScroll);
        split.setResizeWeight(0.6);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        JPanel bottomBar = buildBottomBar();
        add(bottomBar, BorderLayout.SOUTH);

        redirectSystemOut();
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.setBorder(new EmptyBorder(2, 4, 2, 4));

        if (engines.isEmpty()) {
            bar.add(new JLabel("No scripting engine found. Add groovy or bsh to classpath."));
            return bar;
        }

        String[] names = engines.stream().map(JbmScriptEngine::name).toArray(String[]::new);
        engineSelector = new JComboBox<>(names);
        engineSelector.setToolTipText("Select scripting engine");
        bar.add(new JLabel("Engine:"));
        bar.add(engineSelector);

        bar.add(Box.createHorizontalStrut(10));

        JButton runBtn = new JButton("▶ Run");
        runBtn.setToolTipText("Run script (Ctrl+Enter)");
        runBtn.addActionListener(e -> runScript());
        bar.add(runBtn);

        JButton clearBtn = new JButton("Clear Output");
        clearBtn.addActionListener(e -> clearOutput());
        bar.add(clearBtn);

        JButton clearEditorBtn = new JButton("Clear Editor");
        clearEditorBtn.addActionListener(e -> editorArea.setText(""));
        bar.add(clearEditorBtn);

        bar.add(Box.createHorizontalStrut(10));

        JComboBox<String> snippets = buildSnippetMenu();
        bar.add(new JLabel("Snippets:"));
        bar.add(snippets);

        return bar;
    }

    private JComboBox<String> buildSnippetMenu() {
        String[] snippetNames = {
            "-- Insert snippet --",
            "List all class names",
            "Count methods in selected class",
            "Print all LDC strings in selected class",
            "Rename method in selected class",
            "Print all field names in all classes",
            "Print opcode counts in selected method",
        };
        JComboBox<String> combo = new JComboBox<>(snippetNames);
        combo.addActionListener(e -> {
            int idx = combo.getSelectedIndex();
            if (idx <= 0) return;
            editorArea.setText(getSnippet(idx));
            combo.setSelectedIndex(0);
        });
        return combo;
    }

    private String getSnippet(int idx) {
        switch (idx) {
            case 1:
                return "classes.keySet().each { println it }";
            case 2:
                return "def cn = jbm.getCurrentNode()\n"
                     + "if (cn == null) { println 'No class selected'; return }\n"
                     + "println cn.name + ' has ' + cn.methods.size() + ' method(s)'";
            case 3:
                return "import org.objectweb.asm.tree.LdcInsnNode\n"
                     + "import org.objectweb.asm.tree.AbstractInsnNode\n"
                     + "def cn = jbm.getCurrentNode()\n"
                     + "if (cn == null) { println 'No class selected'; return }\n"
                     + "cn.methods.each { mn ->\n"
                     + "    mn.instructions.each { ain ->\n"
                     + "        if (ain instanceof LdcInsnNode && ain.cst instanceof String) {\n"
                     + "            println mn.name + ' -> ' + ain.cst\n"
                     + "        }\n"
                     + "    }\n"
                     + "}";
            case 4:
                return "def cn = jbm.getCurrentNode()\n"
                     + "if (cn == null) { println 'No class selected'; return }\n"
                     + "def oldName = 'myMethod'\n"
                     + "def newName = 'renamedMethod'\n"
                     + "cn.methods.each { mn ->\n"
                     + "    if (mn.name == oldName) {\n"
                     + "        mn.name = newName\n"
                     + "        println 'Renamed ' + oldName + ' -> ' + newName\n"
                     + "    }\n"
                     + "}\n"
                     + "jbm.refreshTree()";
            case 5:
                return "classes.values().each { cn ->\n"
                     + "    cn.fields.each { fn ->\n"
                     + "        println cn.name + '.' + fn.name + ' : ' + fn.desc\n"
                     + "    }\n"
                     + "}";
            case 6:
                return "import me.lpk.util.OpUtils\n"
                     + "def mn = jbm.getCurrentMethod()\n"
                     + "if (mn == null) { println 'No method selected'; return }\n"
                     + "def counts = [:]\n"
                     + "mn.instructions.each { ain ->\n"
                     + "    if (ain.opcode >= 0) {\n"
                     + "        def name = OpUtils.getOpcodeText(ain.opcode)\n"
                     + "        counts[name] = (counts[name] ?: 0) + 1\n"
                     + "    }\n"
                     + "}\n"
                     + "counts.sort { -it.value }.each { k, v -> println k + ': ' + v }";
            default:
                return "";
        }
    }

    private RSyntaxTextArea buildEditor() {
        RSyntaxTextArea area = new RSyntaxTextArea(20, 80);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_GROOVY);
        area.setCodeFoldingEnabled(true);
        area.setAntiAliasingEnabled(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setTabSize(4);
        area.setAutoIndentEnabled(true);

        try {
            boolean dark = Main.INSTANCE.getJByteMod().getOptions().get("use_dark_theme").getBoolean();
            String themeRes = dark
                ? "/resources/de/brownie/rsyntaxtextarea/themes/custom.xml"
                : "/org/fife/ui/rsyntaxtextarea/themes/idea.xml";
            InputStream themeStream = getClass().getResourceAsStream(themeRes);
            if (themeStream != null) {
                Theme.load(themeStream).apply(area);
            }
        } catch (Exception ignored) {}

        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "run-script");
        area.getActionMap().put("run-script", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runScript();
            }
        });

        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "history-prev");
        area.getActionMap().put("history-prev", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateHistory(-1);
            }
        });

        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "history-next");
        area.getActionMap().put("history-next", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                navigateHistory(1);
            }
        });

        area.setText(getWelcomeText());
        return area;
    }

    private String getWelcomeText() {
        return "// Scripting Console\n"
             + "// Available bindings:\n"
             + "//   classes  -> Map<String, ClassNode> (all loaded classes)\n"
             + "//   jbm      -> JByteMod instance\n"
             + "//   asm      -> org.objectweb.asm.Opcodes (all opcode constants)\n"
             + "//\n"
             + "// Ctrl+Enter to run | Alt+Up/Down for history | Snippets menu above\n\n"
             + "println 'Hello from ' + jbm.getTitle()";
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        bar.setBorder(new EmptyBorder(0, 4, 2, 4));
        JLabel hint = new JLabel("Ctrl+Enter: Run  |  Alt+↑/↓: History  |  classes & jbm are pre-bound");
        hint.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        hint.setForeground(Color.GRAY);
        bar.add(hint);
        return bar;
    }

    private void runScript() {
        if (engines.isEmpty()) return;
        String source = editorArea.getText().trim();
        if (source.isEmpty()) return;

        addToHistory(source);

        int engineIdx = engineSelector != null ? engineSelector.getSelectedIndex() : 0;
        JbmScriptEngine engine = engines.get(engineIdx < engines.size() ? engineIdx : 0);

        if (jbm.getJarArchive() != null) {
            engine.bindContext(jbm.getJarArchive().getClasses(), jbm);
        } else {
            engine.setVariable("classes", new java.util.HashMap<>());
            engine.setVariable("jbm", jbm);
            engine.setVariable("asm", org.objectweb.asm.Opcodes.class);
        }

        appendOutput("--- Running [" + engine.name() + "] ---\n", dimStyle);

        SwingWorker<Object, String> worker = new SwingWorker<Object, String>() {
            @Override
            protected Object doInBackground() throws Exception {
                PrintStream oldOut = System.out;
                PrintStream oldErr = System.err;
                ByteArrayOutputStream capture = new ByteArrayOutputStream();
                PrintStream captureStream = new PrintStream(capture) {
                    @Override
                    public void println(String x) {
                        publish(x + "\n");
                    }
                    @Override
                    public void print(String x) {
                        publish(x);
                    }
                };
                System.setOut(captureStream);
                System.setErr(captureStream);
                try {
                    return engine.eval(source);
                } finally {
                    System.setOut(oldOut);
                    System.setErr(oldErr);
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    appendOutput(chunk, normalStyle);
                }
            }

            @Override
            protected void done() {
                try {
                    Object result = get();
                    if (result != null) {
                        appendOutput("=> " + result + "\n", resultStyle);
                    }
                    appendOutput("--- Done ---\n", dimStyle);
                    Main.INSTANCE.getLogger().log("Script executed successfully.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    appendOutput("[ERROR] " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + "\n", errorStyle);
                    Main.INSTANCE.getLogger().err("Script error: " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void appendOutput(String text, AttributeSet style) {
        SwingUtilities.invokeLater(() -> {
            try {
                outputDoc.insertString(outputDoc.getLength(), text, style);
                outputPane.setCaretPosition(outputDoc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private void clearOutput() {
        try {
            outputDoc.remove(0, outputDoc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private void addToHistory(String source) {
        if (!history.isEmpty() && history.get(history.size() - 1).equals(source)) return;
        history.add(source);
        if (history.size() > MAX_HISTORY) history.remove(0);
        historyIndex = history.size();
    }

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;
        historyIndex = Math.max(0, Math.min(history.size() - 1, historyIndex + direction));
        editorArea.setText(history.get(historyIndex));
    }

    private void redirectSystemOut() {
    }

    public void focusEditor() {
        editorArea.requestFocusInWindow();
    }

    public boolean hasEngines() {
        return !engines.isEmpty();
    }
}