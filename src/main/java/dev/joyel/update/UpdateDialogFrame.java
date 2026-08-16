package dev.joyel.update;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.net.URI;

public final class UpdateDialogFrame extends JDialog {

    private UpdateDialogFrame(UpdateRelease release) {
        super((Frame) null, "Update Available \u2013 " + release.getVersion(), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(580, 440);
        setLocationRelativeTo(null);
        setResizable(true);

        JLabel titleLabel = new JLabel(
                "<html><b>Version " + escapeHtml(release.getVersion()) + " is available!</b></html>");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 8, 0));

        JTextArea changelogArea = new JTextArea(release.getChangelog());
        changelogArea.setEditable(false);
        changelogArea.setLineWrap(true);
        changelogArea.setWrapStyleWord(true);
        changelogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        changelogArea.setBackground(UIManager.getColor("TextArea.background"));
        JScrollPane scroll = new JScrollPane(changelogArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Changelog"));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton downloadBtn = new JButton("Download");
        downloadBtn.setFont(downloadBtn.getFont().deriveFont(Font.BOLD));
        downloadBtn.setDefaultCapable(true);
        downloadBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                openUrl(release.getUrl());
                dispose();
            }
        });

        JButton dismissBtn = new JButton("Dismiss");
        dismissBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { dispose(); }
        });

        JLabel urlLabel = new JLabel(
                "<html><font color='gray' size='2'>" + escapeHtml(release.getUrl()) + "</font></html>");

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnPanel.add(urlLabel);
        btnPanel.add(dismissBtn);
        btnPanel.add(downloadBtn);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setBorder(new EmptyBorder(12, 14, 10, 14));
        content.add(titleLabel, BorderLayout.NORTH);
        content.add(scroll,     BorderLayout.CENTER);
        content.add(btnPanel,   BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(downloadBtn);
        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public static void show(UpdateRelease release) {
        new UpdateDialogFrame(release).setVisible(true);
    }

    private static void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {}
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
