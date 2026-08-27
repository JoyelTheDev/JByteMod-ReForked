package dev.joyel.deobf.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.Main;
import dev.joyel.deobf.DeobfPipeline;
import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassRegistry;
import dev.joyel.deobf.api.PassResult;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class DeobfPipelineDialog extends JDialog {

    private final JByteMod jbm;

    private final DefaultListModel<DeobfPass> availableModel = new DefaultListModel<>();
    private final DefaultListModel<DeobfPass> selectedModel  = new DefaultListModel<>();

    private final JList<DeobfPass> availableList = new JList<>(availableModel);
    private final JList<DeobfPass> selectedList  = new JList<>(selectedModel);

    private final JTextArea descArea = new JTextArea();
    private final JCheckBox verboseCheck = new JCheckBox("Verbose logging", false);
    private final JTextArea logArea = new JTextArea();

    private final JButton addBtn    = new JButton("Add \u2192");
    private final JButton removeBtn = new JButton("\u2190 Remove");
    private final JButton upBtn     = new JButton("\u25B2 Up");
    private final JButton downBtn   = new JButton("\u25BC Down");
    private final JButton runBtn    = new JButton("Run Pipeline");
    private final JButton addAllBtn = new JButton("Add Recommended");
    private final JButton clearBtn  = new JButton("Clear");

    public static void open(JByteMod jbm) {
        DeobfPipelineDialog dlg = new DeobfPipelineDialog(jbm);
        dlg.setVisible(true);
    }

    public DeobfPipelineDialog(JByteMod jbm) {
        super(jbm, "Deobfuscation Pipeline", false);
        this.jbm = jbm;

        setSize(900, 620);
        setLocationRelativeTo(jbm);
        setLayout(new BorderLayout(6, 6));

        populateAvailable();
        buildUI();
        wireListeners();
    }

    private void populateAvailable() {
        Map<PassCategory, List<DeobfPass>> byCategory = new LinkedHashMap<>();
        for (PassCategory cat : PassCategory.values()) byCategory.put(cat, new ArrayList<>());
        for (Class<? extends DeobfPass> cls : PassRegistry.getAll()) {
            DeobfPass pass = PassRegistry.instantiate(cls);
            byCategory.get(pass.category).add(pass);
        }
        for (List<DeobfPass> passes : byCategory.values()) {
            for (DeobfPass p : passes) availableModel.addElement(p);
        }
    }

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(6, 6));
        main.setBorder(new EmptyBorder(8, 8, 8, 8));

        main.add(buildSelectionPanel(), BorderLayout.CENTER);
        main.add(buildDescPanel(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                main, buildLogPanel());
        split.setResizeWeight(0.65);

        add(split, BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);
    }

    private JPanel buildSelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Pass Selection"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(3, 3, 3, 3);

        availableList.setCellRenderer(new PassCellRenderer());
        availableList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane leftScroll = new JScrollPane(availableList);
        leftScroll.setBorder(new TitledBorder("Available Passes"));
        leftScroll.setPreferredSize(new Dimension(320, 0));

        selectedList.setCellRenderer(new PassCellRenderer());
        selectedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane rightScroll = new JScrollPane(selectedList);
        rightScroll.setBorder(new TitledBorder("Selected (in execution order)"));

        JPanel middle = new JPanel(new GridLayout(6, 1, 4, 4));
        middle.add(addBtn);
        middle.add(removeBtn);
        middle.add(new JSeparator());
        middle.add(upBtn);
        middle.add(downBtn);
        middle.add(clearBtn);

        gbc.weightx = 0.45; gbc.weighty = 1; gbc.gridx = 0; gbc.gridy = 0;
        panel.add(leftScroll, gbc);

        gbc.weightx = 0.1; gbc.fill = GridBagConstraints.NONE; gbc.gridx = 1;
        panel.add(middle, gbc);

        gbc.weightx = 0.45; gbc.fill = GridBagConstraints.BOTH; gbc.gridx = 2;
        panel.add(rightScroll, gbc);

        return panel;
    }

    private JPanel buildDescPanel() {
        descArea.setEditable(false);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setRows(3);
        descArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        descArea.setBackground(UIManager.getColor("Panel.background"));

        JScrollPane sp = new JScrollPane(descArea);
        sp.setBorder(new TitledBorder("Description"));

        JPanel p = new JPanel(new BorderLayout());
        p.add(sp);
        return p;
    }

    private JScrollPane buildLogPanel() {
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setRows(6);
        JScrollPane sp = new JScrollPane(logArea);
        sp.setBorder(new TitledBorder("Pipeline Log"));
        return sp;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD));
        bar.add(verboseCheck);
        bar.add(addAllBtn);
        bar.add(runBtn);
        return bar;
    }

    private void wireListeners() {
        addBtn.addActionListener(e -> addSelected());
        removeBtn.addActionListener(e -> removeSelected());
        upBtn.addActionListener(e -> moveUp());
        downBtn.addActionListener(e -> moveDown());
        clearBtn.addActionListener(e -> selectedModel.clear());
        addAllBtn.addActionListener(e -> addRecommended());
        runBtn.addActionListener(e -> runPipeline());

        availableList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) addSelected();
            }
        });

        availableList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateDesc(availableList.getSelectedValue());
        });
        selectedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateDesc(selectedList.getSelectedValue());
        });
    }

    private void addSelected() {
        for (DeobfPass pass : availableList.getSelectedValuesList()) {
            boolean alreadyAdded = false;
            for (int i = 0; i < selectedModel.size(); i++) {
                if (selectedModel.get(i).getClass() == pass.getClass()) { alreadyAdded = true; break; }
            }
            if (!alreadyAdded) selectedModel.addElement(PassRegistry.instantiate(pass.getClass()));
        }
    }

    private void removeSelected() {
        int idx = selectedList.getSelectedIndex();
        if (idx >= 0) selectedModel.remove(idx);
    }

    private void moveUp() {
        int idx = selectedList.getSelectedIndex();
        if (idx > 0) {
            DeobfPass p = selectedModel.remove(idx);
            selectedModel.add(idx - 1, p);
            selectedList.setSelectedIndex(idx - 1);
        }
    }

    private void moveDown() {
        int idx = selectedList.getSelectedIndex();
        if (idx >= 0 && idx < selectedModel.size() - 1) {
            DeobfPass p = selectedModel.remove(idx);
            selectedModel.add(idx + 1, p);
            selectedList.setSelectedIndex(idx + 1);
        }
    }

    private void addRecommended() {
        selectedModel.clear();
        for (Class<? extends DeobfPass> cls : PassRegistry.getAll()) {
            DeobfPass pass = PassRegistry.instantiate(cls);
            boolean safe = Arrays.stream(pass.tags).noneMatch(t -> t == PassTag.POSSIBLE_DAMAGE);
            if (safe) selectedModel.addElement(pass);
        }
        logToArea("Added " + selectedModel.size() + " recommended (safe) passes.");
    }

    private void updateDesc(DeobfPass pass) {
        if (pass == null) { descArea.setText(""); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(pass.category.displayName).append("]  ").append(pass.name).append("\n\n");
        sb.append(pass.description).append("\n\n");
        sb.append("Tags: ");
        sb.append(Arrays.stream(pass.tags).map(t -> t.icon + " " + t.description).collect(Collectors.joining("  |  ")));
        if (pass.getAuthor() != null) sb.append("\nAuthor: ").append(pass.getAuthor());
        descArea.setText(sb.toString());
        descArea.setCaretPosition(0);
    }

    private void runPipeline() {
        if (jbm.getJarArchive() == null) {
            JOptionPane.showMessageDialog(this, "No file loaded.", "Pipeline", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No passes selected.", "Pipeline", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<DeobfPass> passes = new ArrayList<>();
        for (int i = 0; i < selectedModel.size(); i++) passes.add(selectedModel.get(i));

        Map<String, ClassNode> classes = jbm.getJarArchive().getClasses();
        boolean verbose = verboseCheck.isSelected();

        logArea.setText("");
        runBtn.setEnabled(false);
        runBtn.setText("Running...");

        new Thread(() -> {
            try {
                DeobfPipeline pipeline = new DeobfPipeline(passes, verbose, msg -> {
                    Main.INSTANCE.getLogger().log(msg);
                    SwingUtilities.invokeLater(() -> logToArea(msg));
                });
                List<PassResult> results = pipeline.run(classes);
                SwingUtilities.invokeLater(() -> {
                    showSummary(results);
                    if (jbm.getCurrentNode() != null && jbm.getCurrentMethod() != null) {
                        jbm.selectMethod(jbm.getCurrentNode(), jbm.getCurrentMethod());
                    }
                });
            } finally {
                SwingUtilities.invokeLater(() -> {
                    runBtn.setEnabled(true);
                    runBtn.setText("Run Pipeline");
                });
            }
        }, "DeobfPipeline-Runner").start();
    }

    private void logToArea(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showSummary(List<PassResult> results) {
        long ok = results.stream().filter(r -> r.success).count();
        StringBuilder sb = new StringBuilder();
        sb.append("Pipeline finished: ").append(ok).append("/").append(results.size()).append(" passes succeeded.\n\n");
        for (PassResult r : results) {
            sb.append(r.success ? "\u2713 " : "\u2717 ").append(r.pass.name)
              .append("  [").append(r.durationMs).append("ms]");
            if (!r.success && r.error != null) sb.append("  \u2192 ").append(r.error);
            sb.append("\n");
        }
        logToArea("\n" + sb);
    }

    private static final class PassCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof DeobfPass) {
                DeobfPass p = (DeobfPass) value;
                boolean dangerous = Arrays.stream(p.tags).anyMatch(t -> t == PassTag.POSSIBLE_DAMAGE);
                setText("[" + p.category.displayName + "]  " + p.name);
                if (!isSelected) {
                    setForeground(dangerous ? new Color(180, 60, 60) : list.getForeground());
                }
            }
            return this;
        }
    }
}
