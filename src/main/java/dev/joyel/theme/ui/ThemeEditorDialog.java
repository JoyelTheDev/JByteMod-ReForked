package dev.joyel.theme.ui;

import dev.joyel.theme.BuiltInThemes;
import dev.joyel.theme.JBytePalette;
import dev.joyel.theme.JByteTheme;
import dev.joyel.theme.ThemeColorCategory;
import dev.joyel.theme.ThemeManager;
import dev.joyel.theme.ThemeToken;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ThemeEditorDialog extends JDialog {
    private final ThemeManager manager = ThemeManager.getInstance();

    private JComboBox<String> themeCombo;
    private JButton applyButton;
    private JButton saveAsButton;
    private JButton deleteButton;
    private JButton resetButton;
    private JTabbedPane categoryTabs;
    private ThemePreviewPanel preview;

    private JByteTheme editingTheme;
    private final Map<String, ColorSwatch> swatches = new LinkedHashMap<String, ColorSwatch>();
    private boolean dirty;

    public ThemeEditorDialog(JFrame parent) {
        super(parent, "Theme Editor", false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI();
        pack();
        setMinimumSize(new Dimension(780, 560));
        setLocationRelativeTo(parent);
        loadThemeIntoEditor(manager.getActiveTheme().deepCopy(manager.getActiveTheme().getName()));
        refreshCombo();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));

        JPanel topBar = buildTopBar();
        root.add(topBar, BorderLayout.NORTH);

        categoryTabs = new JTabbedPane();
        for (ThemeColorCategory cat : ThemeColorCategory.values()) {
            categoryTabs.addTab(cat.getDisplayName(), buildCategoryPanel(cat));
        }

        preview = new ThemePreviewPanel();
        preview.setBorder(BorderFactory.createTitledBorder("Preview"));
        preview.setPreferredSize(new Dimension(0, 160));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, categoryTabs, preview);
        split.setResizeWeight(0.65);
        split.setDividerSize(5);
        root.add(split, BorderLayout.CENTER);

        JPanel bottom = buildBottomBar();
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.GRAY));

        bar.add(new JLabel("Theme:"));
        themeCombo = new JComboBox<String>();
        themeCombo.setPreferredSize(new Dimension(180, 26));
        themeCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onComboSelected();
            }
        });
        bar.add(themeCombo);

        applyButton = new JButton("Apply");
        applyButton.setToolTipText("Apply this theme to the editor now");
        applyButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { applyEditing(); }
        });
        bar.add(applyButton);

        saveAsButton = new JButton("Save As...");
        saveAsButton.setToolTipText("Save a copy of the current edits as a new theme");
        saveAsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { saveAs(); }
        });
        bar.add(saveAsButton);

        deleteButton = new JButton("Delete");
        deleteButton.setToolTipText("Delete this user theme permanently");
        deleteButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { deleteTheme(); }
        });
        bar.add(deleteButton);

        resetButton = new JButton("Reset to Default");
        resetButton.setToolTipText("Discard edits and reload the selected theme");
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { resetEditing(); }
        });
        bar.add(resetButton);

        return bar;
    }

    private JPanel buildCategoryPanel(final ThemeColorCategory category) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        for (ThemeToken token : JBytePalette.all()) {
            if (token.getCategory() != category) continue;

            final String key = token.getKey();

            JLabel nameLabel = new JLabel(token.getLabel());
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
            nameLabel.setPreferredSize(new Dimension(200, 22));

            JTextField hexField = new JTextField(7);
            hexField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            hexField.setHorizontalAlignment(SwingConstants.CENTER);

            ColorSwatch swatch = new ColorSwatch(token.getColor());
            swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            swatch.setToolTipText("Click to pick a color");
            swatches.put(key, swatch);

            syncFieldFromToken(hexField, token.getColor());

            final JTextField finalHexField = hexField;
            swatch.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    openColorPicker(key, finalHexField);
                }
            });

            hexField.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    applyHexField(key, finalHexField);
                }
            });
            hexField.addFocusListener(new java.awt.event.FocusAdapter() {
                public void focusLost(java.awt.event.FocusEvent e) {
                    applyHexField(key, finalHexField);
                }
            });

            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
            panel.add(nameLabel, gbc);
            gbc.gridx = 1; gbc.weightx = 0;
            panel.add(swatch, gbc);
            gbc.gridx = 2; gbc.weightx = 0;
            panel.add(hexField, gbc);

            row++;
        }

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), gbc);

        JScrollPane scroll = new JScrollPane(panel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY));

        JButton importBtn = new JButton("Import .jbtheme...");
        importBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { importTheme(); }
        });
        bar.add(importBtn);

        JButton exportBtn = new JButton("Export .jbtheme...");
        exportBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { exportTheme(); }
        });
        bar.add(exportBtn);

        bar.add(Box.createHorizontalStrut(24));

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { dispose(); }
        });
        bar.add(closeBtn);

        return bar;
    }

    private void openColorPicker(String key, JTextField hexField) {
        Color initial = editingTheme.getColor(key);
        Color chosen = JColorChooser.showDialog(this, "Choose Color — " + key, initial);
        if (chosen == null) return;
        editingTheme.setColor(key, chosen);
        ColorSwatch sw = swatches.get(key);
        if (sw != null) sw.setColor(chosen);
        syncFieldFromToken(hexField, chosen);
        dirty = true;
        refreshPreview();
    }

    private void applyHexField(String key, JTextField hexField) {
        String text = hexField.getText().trim();
        if (!text.startsWith("#")) text = "#" + text;
        try {
            Color c = parseHex(text);
            editingTheme.setColor(key, c);
            ColorSwatch sw = swatches.get(key);
            if (sw != null) sw.setColor(c);
            hexField.setForeground(Color.GREEN.darker());
            dirty = true;
            refreshPreview();
        } catch (IllegalArgumentException e) {
            hexField.setForeground(Color.RED.darker());
        }
    }

    private void syncFieldFromToken(JTextField field, Color color) {
        field.setText(String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()));
        field.setForeground(null);
    }

    private void loadThemeIntoEditor(JByteTheme theme) {
        editingTheme = theme;
        dirty = false;
        for (ThemeToken token : JBytePalette.all()) {
            Color c = theme.getColor(token.getKey());
            ColorSwatch sw = swatches.get(token.getKey());
            if (sw != null) sw.setColor(c);
        }
        refreshSwatchFields();
        refreshPreview();
        updateButtonStates();
    }

    private void refreshSwatchFields() {
        for (ThemeToken token : JBytePalette.all()) {
            Color c = editingTheme.getColor(token.getKey());
            ColorSwatch sw = swatches.get(token.getKey());
            if (sw != null) sw.setColor(c);
        }
    }

    private void refreshPreview() {
        if (preview != null) preview.refresh(editingTheme);
    }

    private void refreshCombo() {
        String current = editingTheme != null ? editingTheme.getName() : null;
        themeCombo.removeAllItems();
        for (JByteTheme t : manager.getThemes()) {
            themeCombo.addItem(t.getName());
        }
        if (current != null) themeCombo.setSelectedItem(current);
        updateButtonStates();
    }

    private void onComboSelected() {
        String selected = (String) themeCombo.getSelectedItem();
        if (selected == null) return;
        for (JByteTheme t : manager.getThemes()) {
            if (t.getName().equals(selected)) {
                loadThemeIntoEditor(t.deepCopy(t.getName()));
                break;
            }
        }
    }

    private void applyEditing() {
        syncEditingToManager();
        manager.applyTheme(findOrAddTheme());
    }

    private void saveAs() {
        String name = JOptionPane.showInputDialog(this, "Enter theme name:", "Save Theme As", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        JByteTheme copy = editingTheme.deepCopy(name);
        if (!manager.addUserTheme(copy)) {
            JOptionPane.showMessageDialog(this, "A theme named \"" + name + "\" already exists.", "Duplicate Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        manager.saveTheme(copy);
        refreshCombo();
        themeCombo.setSelectedItem(name);
        dirty = false;
    }

    private void deleteTheme() {
        String name = (String) themeCombo.getSelectedItem();
        if (name == null) return;
        JByteTheme target = null;
        for (JByteTheme t : manager.getThemes()) {
            if (t.getName().equals(name)) { target = t; break; }
        }
        if (target == null || target.isBuiltIn()) {
            JOptionPane.showMessageDialog(this, "Built-in themes cannot be deleted.", "Cannot Delete", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete theme \"" + name + "\" permanently?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        manager.removeUserTheme(target);
        refreshCombo();
        loadThemeIntoEditor(manager.getActiveTheme().deepCopy(manager.getActiveTheme().getName()));
    }

    private void resetEditing() {
        String name = (String) themeCombo.getSelectedItem();
        if (name == null) return;
        for (JByteTheme t : manager.getThemes()) {
            if (t.getName().equals(name)) {
                loadThemeIntoEditor(t.deepCopy(t.getName()));
                break;
            }
        }
    }

    private void importTheme() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Import Theme");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JByteMod Theme (*.jbtheme)", "jbtheme"));
        if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        java.io.File file = fc.getSelectedFile();
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            fis.close();
            JByteTheme theme = JByteTheme.deserialize(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!manager.addUserTheme(theme)) {
                theme = theme.deepCopy(theme.getName() + " (imported)");
                manager.addUserTheme(theme);
            }
            manager.saveTheme(theme);
            refreshCombo();
            themeCombo.setSelectedItem(theme.getName());
            loadThemeIntoEditor(theme.deepCopy(theme.getName()));
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to import: " + e.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportTheme() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle("Export Theme");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JByteMod Theme (*.jbtheme)", "jbtheme"));
        fc.setSelectedFile(new java.io.File(editingTheme.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".jbtheme"));
        if (fc.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        java.io.File file = fc.getSelectedFile();
        if (!file.getName().endsWith(".jbtheme")) file = new java.io.File(file.getParentFile(), file.getName() + ".jbtheme");
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(editingTheme.serialize().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
            JOptionPane.showMessageDialog(this, "Exported to:\n" + file.getAbsolutePath(), "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to export: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateButtonStates() {
        String name = (String) themeCombo.getSelectedItem();
        boolean builtIn = true;
        if (name != null) {
            for (JByteTheme t : manager.getThemes()) {
                if (t.getName().equals(name)) { builtIn = t.isBuiltIn(); break; }
            }
        }
        deleteButton.setEnabled(!builtIn);
    }

    private void syncEditingToManager() {
        // No-op: edits are already stored in editingTheme
    }

    private JByteTheme findOrAddTheme() {
        String name = editingTheme.getName();
        for (JByteTheme t : manager.getThemes()) {
            if (t.getName().equals(name)) {
                t.getColors().putAll(editingTheme.getColors());
                return t;
            }
        }
        manager.addUserTheme(editingTheme);
        return editingTheme;
    }

    private static Color parseHex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 6) {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return new Color(r, g, b);
        }
        if (h.length() == 8) {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            int a = Integer.parseInt(h.substring(6, 8), 16);
            return new Color(r, g, b, a);
        }
        throw new IllegalArgumentException("Invalid hex color: " + hex);
    }
}
