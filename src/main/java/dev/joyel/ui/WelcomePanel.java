package dev.joyel.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.RecentFiles;
import de.xbrowniecodez.jbytemod.ui.FileDropSupport;
import me.grax.jbytemod.ui.MyMenuBar;
import me.grax.jbytemod.utils.gui.Icons;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.util.List;

public final class WelcomePanel extends JPanel {
    private static final int CONTENT_WIDTH = 520;

    private final JByteMod jbm;
    private final DefaultListModel<File> recentModel = new DefaultListModel<>();
    private final JList<File> recentList = new JList<>(recentModel);
    private final JPanel recentSection = new JPanel(new BorderLayout(0, 6));
    private final JLabel emptyRecentLabel = new JLabel("No recent files yet", SwingConstants.CENTER);
    private final JScrollPane recentScroll = new JScrollPane(recentList);
    private final JButton clearButton = new JButton("Clear list");

    public WelcomePanel(JByteMod jbm) {
        this.jbm = jbm;
        setLayout(new GridBagLayout());
        setOpaque(true);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new javax.swing.BoxLayout(column, javax.swing.BoxLayout.Y_AXIS));

        JLabel logo = new JLabel(loadLogo());
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel("JByteMod ReForked");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 10f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel subtitle = new JLabel("Open a file to start editing bytecode");
        subtitle.setForeground(mutedColor());
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        column.add(logo);
        column.add(javax.swing.Box.createVerticalStrut(8));
        column.add(title);
        column.add(javax.swing.Box.createVerticalStrut(4));
        column.add(subtitle);
        column.add(javax.swing.Box.createVerticalStrut(20));
        column.add(createDropZone());
        column.add(javax.swing.Box.createVerticalStrut(14));
        column.add(createActionRow());
        column.add(javax.swing.Box.createVerticalStrut(22));
        configureRecentSection();
        recentSection.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(recentSection);

        add(column, new GridBagConstraints());

        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                refreshRecent();
            }
        });
        refreshRecent();
    }

    private Icon loadLogo() {
        try {
            URL url = getClass().getClassLoader().getResource("resources/logo.png");
            if (url != null) {
                Image image = ImageIO.read(url);
                if (image != null) {
                    return new ImageIcon(image.getScaledInstance(72, 72, Image.SCALE_SMOOTH));
                }
            }
        } catch (Exception ignored) {
        }
        return Icons.svg("load", 56);
    }

    private JPanel createDropZone() {
        JPanel zone = new JPanel(new GridBagLayout()) {
            private boolean hover;

            {
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent event) {
                        hover = false;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent event) {
                        openFile();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color accent = accentColor();
                if (hover) {
                    g.setColor(withAlpha(accent, 28));
                    g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                }
                g.setColor(hover ? accent : borderColor());
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 5f}, 0f));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                g.dispose();
            }
        };
        zone.setAlignmentX(Component.CENTER_ALIGNMENT);
        zone.setPreferredSize(new Dimension(CONTENT_WIDTH, 130));
        zone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 6, 0);
        zone.add(new JLabel(Icons.svg("load", 40)), c);
        JLabel primary = new JLabel("Drop a .jar, .class or .apk file here");
        primary.setFont(primary.getFont().deriveFont(Font.BOLD));
        c.gridy = 1;
        c.insets = new java.awt.Insets(0, 0, 2, 0);
        zone.add(primary, c);
        JLabel secondary = new JLabel("or click to browse");
        secondary.setForeground(mutedColor());
        c.gridy = 2;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        zone.add(secondary, c);
        return zone;
    }

    private JPanel createActionRow() {
        JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        JButton open = new JButton("Open file...", Icons.svg("load", 18));
        open.setFocusable(false);
        open.addActionListener(event -> openFile());
        JButton attach = new JButton("Attach to process...", Icons.svg("plug", 18));
        attach.setFocusable(false);
        attach.addActionListener(event -> attachToProcess());
        row.add(open);
        row.add(attach);
        return row;
    }

    private void configureRecentSection() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel heading = new JLabel("Recent files");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        header.add(heading, BorderLayout.WEST);
        clearButton.setFocusable(false);
        clearButton.setBorderPainted(false);
        clearButton.setContentAreaFilled(false);
        clearButton.setForeground(accentColor());
        clearButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearButton.addActionListener(event -> {
            RecentFiles.getInstance().clear();
            refreshRecent();
        });
        header.add(clearButton, BorderLayout.EAST);

        recentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentList.setCellRenderer(new RecentRenderer());
        recentList.setFixedCellHeight(48);
        recentList.setVisibleRowCount(5);
        recentList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-recent");
        recentList.getActionMap().put("open-recent", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                openRecent(recentList.getSelectedValue());
            }
        });
        recentList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "remove-recent");
        recentList.getActionMap().put("remove-recent", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                removeRecent(recentList.getSelectedValue());
            }
        });
        recentList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                int index = recentList.locationToIndex(event.getPoint());
                if (index >= 0 && recentList.getCellBounds(index, index).contains(event.getPoint())) {
                    recentList.setSelectedIndex(index);
                } else {
                    recentList.clearSelection();
                }
                if (event.isPopupTrigger()) {
                    showRecentMenu(event);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) {
                    showRecentMenu(event);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1) {
                    int index = recentList.locationToIndex(event.getPoint());
                    if (index >= 0 && recentList.getCellBounds(index, index).contains(event.getPoint())) {
                        openRecent(recentModel.get(index));
                    }
                }
            }
        });
        recentList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        recentScroll.setBorder(BorderFactory.createLineBorder(borderColor()));
        recentScroll.setPreferredSize(new Dimension(CONTENT_WIDTH, 5 * 48 + 2));
        emptyRecentLabel.setForeground(mutedColor());
        emptyRecentLabel.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));

        recentSection.setOpaque(false);
        recentSection.add(header, BorderLayout.NORTH);
        recentSection.add(recentScroll, BorderLayout.CENTER);
        recentSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5 * 48 + 56));
    }

    private void showRecentMenu(MouseEvent event) {
        int index = recentList.locationToIndex(event.getPoint());
        if (index < 0 || !recentList.getCellBounds(index, index).contains(event.getPoint())) {
            return;
        }
        File file = recentModel.get(index);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(e -> openRecent(file));
        JMenuItem remove = new JMenuItem("Remove from list");
        remove.addActionListener(e -> removeRecent(file));
        JMenuItem copy = new JMenuItem("Copy path");
        copy.addActionListener(e -> copyToClipboard(file.getAbsolutePath()));
        menu.add(open);
        menu.add(copy);
        menu.addSeparator();
        menu.add(remove);
        menu.show(recentList, event.getX(), event.getY());
    }

    private static void copyToClipboard(String text) {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
    }

    public void refreshRecent() {
        List<File> files = RecentFiles.getInstance().getFiles();
        recentModel.clear();
        for (File file : files) {
            recentModel.addElement(file);
        }
        recentSection.remove(recentScroll);
        recentSection.remove(emptyRecentLabel);
        if (files.isEmpty()) {
            recentSection.add(emptyRecentLabel, BorderLayout.CENTER);
        } else {
            recentSection.add(recentScroll, BorderLayout.CENTER);
        }
        clearButton.setVisible(!files.isEmpty());
        recentSection.revalidate();
        recentSection.repaint();
    }

    private void openFile() {
        if (jbm.getJMenuBar() instanceof MyMenuBar menuBar) {
            menuBar.openLoadDialogue();
        }
    }

    private void attachToProcess() {
        if (jbm.getJMenuBar() instanceof MyMenuBar menuBar) {
            menuBar.openProcessSelection();
        }
    }

    private void openRecent(File file) {
        if (file == null) {
            return;
        }
        if (!FileDropSupport.isSupported(file)) {
            int choice = JOptionPane.showConfirmDialog(this,
                    file.getName() + " could not be found.\nRemove it from the recent files list?",
                    "File not found", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                removeRecent(file);
            }
            return;
        }
        jbm.loadFile(file);
    }

    private void removeRecent(File file) {
        if (file == null) {
            return;
        }
        RecentFiles.getInstance().remove(file);
        refreshRecent();
    }

    private static Color mutedColor() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }

    private static Color accentColor() {
        Color color = UIManager.getColor("Component.accentColor");
        if (color == null) {
            color = UIManager.getColor("Actions.Blue");
        }
        return color != null ? color : new Color(0x3592C4);
    }

    private static Color borderColor() {
        Color color = UIManager.getColor("Component.borderColor");
        if (color == null) {
            color = UIManager.getColor("Separator.foreground");
        }
        return color != null ? color : Color.LIGHT_GRAY;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static final class RecentRenderer extends JPanel implements ListCellRenderer<File> {
        private final JLabel name = new JLabel();
        private final JLabel path = new JLabel();

        private RecentRenderer() {
            setLayout(new BorderLayout(0, 2));
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            path.setFont(path.getFont().deriveFont(path.getFont().getSize2D() - 1f));
            add(name, BorderLayout.NORTH);
            add(path, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends File> list, File file, int index,
                                                      boolean selected, boolean focused) {
            boolean exists = file.isFile();
            name.setText(exists ? file.getName() : file.getName() + " (missing)");
            File parent = file.getParentFile();
            path.setText(parent == null ? file.getPath() : parent.getPath());
            setToolTipText(file.getAbsolutePath());
            if (selected) {
                setBackground(list.getSelectionBackground());
                name.setForeground(list.getSelectionForeground());
                path.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                name.setForeground(exists ? list.getForeground() : mutedColor());
                path.setForeground(mutedColor());
            }
            setOpaque(true);
            return this;
        }
    }
}
