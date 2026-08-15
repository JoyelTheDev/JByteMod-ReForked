package dev.joyel.methodgraph;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import me.grax.jbytemod.JarArchive;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MethodGraphFrame extends JDialog {

    private static final int[] DEPTHS = {1, 2, 3, 4, 5, MethodGraphAnalyzer.INFINITE_DEPTH};
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 2)),
            r -> { Thread t = new Thread(r, "MethodGraph-Worker"); t.setDaemon(true); return t; });

    private final JByteMod jbm;
    private String rootOwner;
    private MethodNode rootMethod;

    private int depth = 2;
    private MethodGraph.Direction direction = MethodGraph.Direction.CALLS;
    private boolean includeExternal = true;

    private final MethodGraphCanvas canvas;
    private final JComboBox<String> directionCombo;
    private final JComboBox<String> depthCombo;
    private final JCheckBox externalCheck;
    private final JTextField searchField;
    private final JLabel statusLabel;
    private final JLabel analysingLabel;

    private final AtomicInteger generation = new AtomicInteger();
    private CompletableFuture<?> future;
    private volatile PendingResult pending;
    private MethodGraph graph;

    private final List<MethodGraph.MethodKey> searchMatches = new ArrayList<>();
    private int searchIndex = -1;

    public MethodGraphFrame(JByteMod jbm, String owner, MethodNode method) {
        super((Frame) null, "Method Graph – " + shortName(owner) + "." + method.name, false);
        this.jbm = jbm;
        this.rootOwner  = owner;
        this.rootMethod = method;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(jbm);

        canvas = new MethodGraphCanvas(new CanvasActions());

        directionCombo = new JComboBox<>(Arrays.stream(MethodGraph.Direction.values())
                .map(MethodGraph.Direction::getLabel).toArray(String[]::new));
        directionCombo.setSelectedIndex(0);
        directionCombo.setMaximumSize(new Dimension(100, 26));
        directionCombo.addActionListener(e -> { direction = MethodGraph.Direction.values()[directionCombo.getSelectedIndex()]; requestAnalysis(); });

        depthCombo = new JComboBox<>(Arrays.stream(DEPTHS)
                .mapToObj(d -> d == MethodGraphAnalyzer.INFINITE_DEPTH ? "∞" : String.valueOf(d))
                .toArray(String[]::new));
        depthCombo.setSelectedIndex(1);
        depthCombo.setMaximumSize(new Dimension(70, 26));
        depthCombo.addActionListener(e -> { depth = DEPTHS[depthCombo.getSelectedIndex()]; requestAnalysis(); });

        externalCheck = new JCheckBox("External", true);
        externalCheck.setToolTipText("Include unresolved / dependency methods as leaf nodes");
        externalCheck.addActionListener(e -> { includeExternal = externalCheck.isSelected(); requestAnalysis(); });

        searchField = new JTextField(18);
        searchField.putClientProperty("JTextField.placeholderText", "Find method…");
        searchField.addActionListener(e -> cycleSearch());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { rebuildSearchMatches(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { rebuildSearchMatches(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { rebuildSearchMatches(); }
        });

        statusLabel   = new JLabel("");
        statusLabel.setForeground(Color.GRAY);
        analysingLabel = new JLabel("Analysing…");
        analysingLabel.setForeground(new Color(0x569cd6));
        analysingLabel.setVisible(false);

        JButton fitBtn   = toolButton("Fit [F]",   () -> canvas.requestFit());
        JButton rootBtn  = toolButton("Root",      () -> canvas.centerRoot());
        JButton resetBtn = toolButton("Reset",     () -> canvas.resetLayout());
        JButton rebuildBtn = toolButton("Rebuild", this::requestAnalysis);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(4, 6, 4, 6));
        toolbar.add(label("Direction")); toolbar.addSeparator(new Dimension(4, 0)); toolbar.add(directionCombo);
        toolbar.addSeparator(new Dimension(10, 0));
        toolbar.add(label("Depth")); toolbar.addSeparator(new Dimension(4, 0)); toolbar.add(depthCombo);
        toolbar.addSeparator(new Dimension(10, 0));
        toolbar.add(externalCheck);
        toolbar.addSeparator(new Dimension(10, 0));
        toolbar.add(fitBtn); toolbar.add(rootBtn); toolbar.add(resetBtn); toolbar.add(rebuildBtn);
        toolbar.addSeparator(new Dimension(14, 0));
        toolbar.add(searchField);
        toolbar.addSeparator(new Dimension(14, 0));
        toolbar.add(statusLabel);
        toolbar.addSeparator(new Dimension(8, 0));
        toolbar.add(analysingLabel);

        JSeparator sep = new JSeparator();

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.add(toolbar, BorderLayout.NORTH);
        content.add(sep,    BorderLayout.CENTER);
        content.add(canvas, BorderLayout.SOUTH);
        content.setLayout(new BorderLayout());
        content.add(toolbar, BorderLayout.NORTH);
        content.add(canvas,  BorderLayout.CENTER);

        setContentPane(content);

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { requestAnalysis(); }
            @Override public void windowClosed(WindowEvent e) { cancelAnalysis(); }
        });

        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F && !searchField.isFocusOwner()) {
                    searchField.requestFocusInWindow();
                    searchField.selectAll();
                }
            }
        });

        Timer uiPoller = new Timer(40, e -> applyPending());
        uiPoller.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { uiPoller.stop(); }
        });
    }

    public static void open(JByteMod jbm, String owner, MethodNode method) {
        SwingUtilities.invokeLater(() -> {
            MethodGraphFrame f = new MethodGraphFrame(jbm, owner, method);
            f.setVisible(true);
        });
    }

    private void requestAnalysis() {
        int gen = generation.incrementAndGet();
        cancelAnalysis();
        analysingLabel.setVisible(true);
        statusLabel.setText("");

        JarArchive archive = jbm.getJarArchive();
        String owner    = rootOwner;
        MethodNode method = rootMethod;
        int d  = depth;
        MethodGraph.Direction dir = direction;
        boolean ext = includeExternal;

        future = CompletableFuture.supplyAsync(() -> {
            MethodGraphAnalyzer analyser = new MethodGraphAnalyzer(archive);
            MethodGraphAnalyzer.Request req = new MethodGraphAnalyzer.Request(d, dir, ext);
            return analyser.analyze(owner, method, req,
                    () -> gen != generation.get());
        }, EXECUTOR).whenComplete((result, err) -> {
            if (gen != generation.get()) return;
            pending = new PendingResult(gen, result, err);
        });
    }

    private void cancelAnalysis() {
        if (future != null) future.cancel(true);
    }

    private void applyPending() {
        PendingResult p = pending;
        if (p == null) return;
        pending = null;
        if (p.generation() != generation.get()) return;
        analysingLabel.setVisible(false);

        Throwable err = unwrap(p.error());
        if (err != null) {
            if (!(err instanceof CancellationException)) {
                String msg = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
                statusLabel.setForeground(new Color(0xe05555));
                statusLabel.setText("Error: " + msg);
            }
            return;
        }

        graph = p.graph();
        canvas.setGraph(graph);
        rebuildSearchMatches();
        updateStatus();
    }

    private void updateStatus() {
        if (graph == null) return;
        int calls = graph.calls().stream().mapToInt(MethodGraph.CallEdge::callSites).sum();
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setText(graph.nodes().size() + " methods  " + calls + " calls");
    }

    private void rebuildSearchMatches() {
        searchMatches.clear();
        searchIndex = -1;
        if (graph == null) return;
        String q = searchField.getText().strip().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) { canvas.repaint(); return; }
        graph.nodes().values().stream()
                .filter(n -> n.key().symbol().toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(n -> n.key().symbol()))
                .forEach(n -> searchMatches.add(n.key()));
        canvas.repaint();
    }

    private void cycleSearch() {
        if (searchMatches.isEmpty()) return;
        searchIndex = (searchIndex + 1) % searchMatches.size();
        canvas.selectAndCenter(searchMatches.get(searchIndex));
    }

    private void showNodeContextMenu(MethodGraph.MethodNode node, Component parent, int x, int y) {
        JPopupMenu popup = new JPopupMenu();
        if (!node.external()) {
            JMenuItem setRoot = new JMenuItem("Set as Graph Root");
            setRoot.addActionListener(e -> setGraphRoot(node));
            popup.add(setRoot);
            JMenuItem openEditor = new JMenuItem("Open in Editor");
            openEditor.addActionListener(e -> openInEditor(node));
            popup.add(openEditor);
            popup.addSeparator();
        }
        JMenuItem copySymbol = new JMenuItem("Copy Symbol");
        copySymbol.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection(node.key().symbol()), null);
        });
        popup.add(copySymbol);
        popup.show(parent, x, y);
    }

    private void setGraphRoot(MethodGraph.MethodNode node) {
        if (node.asmNode() == null) return;
        rootOwner  = node.key().owner();
        rootMethod = node.asmNode();
        setTitle("Method Graph – " + shortName(rootOwner) + "." + rootMethod.name);
        requestAnalysis();
        canvas.requestFit();
    }

    private void openInEditor(MethodGraph.MethodNode node) {
        if (jbm.getJarArchive() == null) return;
        ClassNode cn = jbm.getJarArchive().getClasses().get(node.key().owner());
        if (cn == null || node.asmNode() == null) return;
        jbm.selectMethod(cn, node.asmNode());
    }

    private static Throwable unwrap(Throwable t) {
        while (t instanceof CompletionException || t instanceof ExecutionException) {
            if (t.getCause() == null) break;
            t = t.getCause();
        }
        return t;
    }

    private static String shortName(String owner) {
        int i = owner.lastIndexOf('/');
        return i == -1 ? owner : owner.substring(i + 1);
    }

    private static JButton toolButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.GRAY);
        return l;
    }

    private final class CanvasActions implements MethodGraphCanvas.Actions {
        @Override
        public void onDoubleClick(MethodGraph.MethodNode node) {
            openInEditor(node);
        }

        @Override
        public void showContextMenu(MethodGraph.MethodNode node, Component parent, int x, int y) {
            showNodeContextMenu(node, parent, x, y);
        }
    }

    private record PendingResult(int generation, MethodGraph graph, Throwable error) {}
}
