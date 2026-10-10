package dev.joyel.update;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URI;

public final class UpdateDialogFrame extends JDialog {
    private UpdateDialogFrame(UpdateRelease release) {
        super((Frame) null, "Update Available \u2013 " + release.getVersion(), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(580, 440);
        setLocationRelativeTo(null);
        setResizable(true);

        JLabel titleLabel = createTitleLabel(release);
        JScrollPane scrollPane = createChangelogScrollPane(release);
        JButton downloadBtn = createDownloadButton(release);
        JButton dismissBtn = createDismissButton();
        JLabel urlLabel = createUrlLabel(release);
        JPanel buttonPanel = createButtonPanel(urlLabel, dismissBtn, downloadBtn);

        configureContentPane(titleLabel, scrollPane, buttonPanel);
        configureKeyboardShortcuts();
        getRootPane().setDefaultButton(downloadBtn);
    }

    public static void show(UpdateRelease release) {
        SwingUtilities.invokeLater(() -> new UpdateDialogFrame(release).setVisible(true));
    }

    private JLabel createTitleLabel(UpdateRelease release) {
        JLabel label = new JLabel(
                "<html><b>Version " + escapeHtml(release.getVersion()) + " is available!</b></html>"
        );
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
    }

    private JScrollPane createChangelogScrollPane(UpdateRelease release) {
        JTextArea changelogArea = new JTextArea(release.getChangelog());
        changelogArea.setEditable(false);
        changelogArea.setLineWrap(true);
        changelogArea.setWrapStyleWord(true);
        changelogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        changelogArea.setBackground(UIManager.getColor("TextArea.background"));

        JScrollPane scrollPane = new JScrollPane(changelogArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Changelog"));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JButton createDownloadButton(UpdateRelease release) {
        JButton button = new JButton("Download");
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setDefaultCapable(true);
        button.addActionListener(e -> {
            openUrl(release.getUrl());
            dispose();
        });
        return button;
    }

    private JButton createDismissButton() {
        JButton button = new JButton("Dismiss");
        button.addActionListener(e -> dispose());
        return button;
    }

    private JLabel createUrlLabel(UpdateRelease release) {
        return new JLabel(
                "<html><font color='gray' size='2'>" + escapeHtml(release.getUrl()) + "</font></html>"
        );
    }

    private JPanel createButtonPanel(JLabel urlLabel, JButton dismissBtn, JButton downloadBtn) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panel.add(urlLabel);
        panel.add(dismissBtn);
        panel.add(downloadBtn);
        return panel;
    }

    private void configureContentPane(JLabel titleLabel, JScrollPane scrollPane, JPanel buttonPanel) {
        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(new EmptyBorder(12, 14, 10, 14));
        content.add(titleLabel, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void configureKeyboardShortcuts() {
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}