package dev.joyel.tutorial;

import de.xbrowniecodez.jbytemod.JByteMod;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public final class TutorialDialog extends JDialog {
    private final JByteMod jbm;
    private final List<TutorialStep> steps;
    private final TutorialOverlay overlay;
    private int currentIndex = 0;

    private final JLabel titleLabel;
    private final JEditorPane bodyPane;
    private final JButton previousButton;
    private final JButton nextButton;
    private final JButton closeButton;
    private final JLabel stepCountLabel;
    private final TutorialProgressBar progressBar;
    private final JCheckBox dontShowAgain;

    public TutorialDialog(JByteMod jbm) {
        super(jbm, "JByteMod Tutorial", false);
        this.jbm = jbm;
        this.steps = TutorialSteps.buildSteps();
        this.overlay = new TutorialOverlay();
        this.overlay.setOpaque(false);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeTutorial();
            }
        });

        setSize(460, 380);
        setResizable(false);
        setLocationRelativeTo(jbm);

        JPanel rootPanel = new JPanel(new BorderLayout(0, 0));
        rootPanel.setBackground(new Color(30, 30, 30));
        rootPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        setContentPane(rootPanel);

        JPanel headerPanel = createHeaderPanel();
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = createBodyScrollPane();
        rootPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel footerPanel = createFooterPanel();
        rootPanel.add(footerPanel, BorderLayout.SOUTH);

        currentIndex = Math.min(TutorialPrefs.getSavedStep(), steps.size() - 1);
        showStep(currentIndex);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(new Color(40, 40, 40));
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

        titleLabel = new JLabel();
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLabel.setForeground(new Color(255, 200, 0));
        header.add(titleLabel, BorderLayout.CENTER);

        stepCountLabel = new JLabel();
        stepCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        stepCountLabel.setForeground(new Color(160, 160, 160));
        stepCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        header.add(stepCountLabel, BorderLayout.EAST);

        return header;
    }

    private JScrollPane createBodyScrollPane() {
        bodyPane = new JEditorPane();
        bodyPane.setContentType("text/html");
        bodyPane.setEditable(false);
        bodyPane.setOpaque(true);
        bodyPane.setBackground(new Color(38, 38, 38));
        bodyPane.setForeground(new Color(220, 220, 220));
        bodyPane.setBorder(new EmptyBorder(10, 14, 10, 14));
        bodyPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        bodyPane.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JScrollPane scrollPane = new JScrollPane(bodyPane);
        scrollPane.setBorder(null);
        scrollPane.setBackground(new Color(38, 38, 38));
        return scrollPane;
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(6, 4));
        footer.setBackground(new Color(40, 40, 40));
        footer.setBorder(new EmptyBorder(8, 14, 10, 14));

        progressBar = new TutorialProgressBar();
        progressBar.setBackground(new Color(40, 40, 40));
        progressBar.setOpaque(true);
        footer.add(progressBar, BorderLayout.NORTH);

        dontShowAgain = new JCheckBox("Don't show this on startup");
        dontShowAgain.setBackground(new Color(40, 40, 40));
        dontShowAgain.setForeground(new Color(160, 160, 160));
        dontShowAgain.setFont(new Font("SansSerif", Font.PLAIN, 11));
        dontShowAgain.setSelected(TutorialPrefs.hasSeenTutorial());

        JPanel navigationRow = createNavigationRow();
        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setBackground(new Color(40, 40, 40));
        bottomRow.add(dontShowAgain, BorderLayout.WEST);
        bottomRow.add(navigationRow, BorderLayout.EAST);

        footer.add(bottomRow, BorderLayout.CENTER);
        return footer;
    }

    private JPanel createNavigationRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setBackground(new Color(40, 40, 40));

        previousButton = createNavigationButton("\u2190 Back");
        nextButton = createNavigationButton("Next \u2192");
        closeButton = createNavigationButton("Close");
        closeButton.setForeground(new Color(180, 80, 80));

        previousButton.addActionListener(e -> {
            if (currentIndex > 0) {
                currentIndex--;
                TutorialPrefs.saveStep(currentIndex);
                showStep(currentIndex);
            }
        });

        nextButton.addActionListener(e -> {
            if (currentIndex < steps.size() - 1) {
                currentIndex++;
                TutorialPrefs.saveStep(currentIndex);
                showStep(currentIndex);
            } else {
                closeTutorial();
            }
        });

        closeButton.addActionListener(e -> closeTutorial());

        row.add(previousButton);
        row.add(nextButton);
        row.add(closeButton);

        return row;
    }

    private JButton createNavigationButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(new Color(55, 55, 55));
        button.setForeground(new Color(220, 220, 220));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void showStep(int index) {
        TutorialStep step = steps.get(index);

        titleLabel.setText(step.getTitle());
        bodyPane.setText(step.getBody());
        bodyPane.setCaretPosition(0);

        stepCountLabel.setText((index + 1) + " / " + steps.size());
        progressBar.update(index, steps.size());

        previousButton.setEnabled(index > 0);
        nextButton.setText(index == steps.size() - 1 ? "Finish" : "Next \u2192");

        applyOverlay(step.getHighlight());
    }

    private void applyOverlay(String highlight) {
        JRootPane root = jbm.getRootPane();
        if (root == null) {
            overlay.clearTarget();
            return;
        }

        JLayeredPane layeredPane = root.getLayeredPane();
        if (overlay.getParent() != layeredPane) {
            overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            layeredPane.add(overlay, JLayeredPane.MODAL_LAYER);
        }

        Component target = resolveTarget(highlight);
        if (target != null) {
            overlay.setTarget(target);
            overlay.setVisible(true);
        } else {
            overlay.clearTarget();
            overlay.setVisible(false);
        }
    }

    private Component resolveTarget(String highlight) {
        if (highlight == null || TutorialStep.HIGHLIGHT_NONE.equals(highlight)) {
            return null;
        }
        if (TutorialStep.HIGHLIGHT_TREE.equals(highlight)) {
            return jbm.getJarTree();
        }
        if (TutorialStep.HIGHLIGHT_CODELIST.equals(highlight)) {
            return jbm.getCodeList();
        }
        if (TutorialStep.HIGHLIGHT_MENUBAR.equals(highlight)) {
            return jbm.getMyMenuBar();
        }
        if (TutorialStep.HIGHLIGHT_TABS.equals(highlight)) {
            return jbm.getTabbedPane();
        }
        if (TutorialStep.HIGHLIGHT_INFOBAR.equals(highlight)) {
            return jbm.getPageEndPanel();
        }
        if (TutorialStep.HIGHLIGHT_SEARCH.equals(highlight)) {
            return jbm.getSearchList();
        }
        if (TutorialStep.HIGHLIGHT_TOOLBAR.equals(highlight)) {
            return jbm.getToolbar();
        }
        return null;
    }

    private void closeTutorial() {
        removeOverlay();
        if (dontShowAgain.isSelected()) {
            TutorialPrefs.markSeen();
        } else {
            TutorialPrefs.reset();
        }
        dispose();
    }

    private void removeOverlay() {
        if (overlay.getParent() != null) {
            overlay.getParent().remove(overlay);
            overlay.getParent().repaint();
        }
    }

    public static void open(JByteMod jbm) {
        SwingUtilities.invokeLater(() -> {
            TutorialDialog dialog = new TutorialDialog(jbm);
            dialog.setVisible(true);
        });
    }

    public static void openIfFirstTime(JByteMod jbm) {
        if (!TutorialPrefs.hasSeenTutorial()) {
            open(jbm);
        }
    }
}