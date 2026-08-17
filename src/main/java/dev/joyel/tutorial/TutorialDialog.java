package dev.joyel.tutorial;

import de.xbrowniecodez.jbytemod.JByteMod;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
    private final JButton prevBtn;
    private final JButton nextBtn;
    private final JButton closeBtn;
    private final JLabel stepCountLabel;
    private final TutorialProgressBar progressBar;
    private final JCheckBox dontShowAgain;

    public TutorialDialog(JByteMod jbm) {
        super(jbm, "JByteMod Tutorial", false);
        this.jbm   = jbm;
        this.steps = TutorialSteps.buildSteps();

        this.overlay = new TutorialOverlay();
        this.overlay.setOpaque(false);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                closeTutorial();
            }
        });

        setSize(460, 380);
        setResizable(false);
        setLocationRelativeTo(jbm);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 30, 30));
        root.setBorder(new EmptyBorder(0, 0, 0, 0));
        setContentPane(root);

        JPanel headerPanel = new JPanel(new BorderLayout(6, 0));
        headerPanel.setBackground(new Color(40, 40, 40));
        headerPanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        titleLabel = new JLabel();
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLabel.setForeground(new Color(255, 200, 0));
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        stepCountLabel = new JLabel();
        stepCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        stepCountLabel.setForeground(new Color(160, 160, 160));
        stepCountLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        headerPanel.add(stepCountLabel, BorderLayout.EAST);

        root.add(headerPanel, BorderLayout.NORTH);

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
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout(6, 4));
        footerPanel.setBackground(new Color(40, 40, 40));
        footerPanel.setBorder(new EmptyBorder(8, 14, 10, 14));

        progressBar = new TutorialProgressBar();
        progressBar.setBackground(new Color(40, 40, 40));
        progressBar.setOpaque(true);
        footerPanel.add(progressBar, BorderLayout.NORTH);

        dontShowAgain = new JCheckBox("Don't show this on startup");
        dontShowAgain.setBackground(new Color(40, 40, 40));
        dontShowAgain.setForeground(new Color(160, 160, 160));
        dontShowAgain.setFont(new Font("SansSerif", Font.PLAIN, 11));
        dontShowAgain.setSelected(TutorialPrefs.hasSeenTutorial());

        JPanel navRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        navRow.setBackground(new Color(40, 40, 40));

        prevBtn = makeNavButton("\u2190 Back");
        nextBtn = makeNavButton("Next \u2192");
        closeBtn = makeNavButton("Close");
        closeBtn.setForeground(new Color(180, 80, 80));

        prevBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (currentIndex > 0) {
                    currentIndex--;
                    TutorialPrefs.saveStep(currentIndex);
                    showStep(currentIndex);
                }
            }
        });

        nextBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (currentIndex < steps.size() - 1) {
                    currentIndex++;
                    TutorialPrefs.saveStep(currentIndex);
                    showStep(currentIndex);
                } else {
                    closeTutorial();
                }
            }
        });

        closeBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                closeTutorial();
            }
        });

        navRow.add(prevBtn);
        navRow.add(nextBtn);
        navRow.add(closeBtn);

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setBackground(new Color(40, 40, 40));
        bottomRow.add(dontShowAgain, BorderLayout.WEST);
        bottomRow.add(navRow, BorderLayout.EAST);

        footerPanel.add(bottomRow, BorderLayout.CENTER);
        root.add(footerPanel, BorderLayout.SOUTH);

        currentIndex = Math.min(TutorialPrefs.getSavedStep(), steps.size() - 1);
        showStep(currentIndex);
    }

    private JButton makeNavButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(55, 55, 55));
        btn.setForeground(new Color(220, 220, 220));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void showStep(int index) {
        TutorialStep step = steps.get(index);

        titleLabel.setText(step.getTitle());
        bodyPane.setText(step.getBody());
        bodyPane.setCaretPosition(0);

        stepCountLabel.setText((index + 1) + " / " + steps.size());
        progressBar.update(index, steps.size());

        prevBtn.setEnabled(index > 0);
        nextBtn.setText(index == steps.size() - 1 ? "Finish" : "Next \u2192");

        applyOverlay(step.getHighlight());
    }

    private void applyOverlay(String highlight) {
        JRootPane root = jbm.getRootPane();
        if (root == null) {
            overlay.clearTarget();
            return;
        }

        JLayeredPane layered = root.getLayeredPane();
        if (overlay.getParent() != layered) {
            overlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
            layered.add(overlay, JLayeredPane.MODAL_LAYER);
        }

        Component target = resolveTarget(highlight);
        if (target != null) {
            overlay.setTarget(target);
            overlay.setVisible(true);
        } else {
            overlay.clearTarget();
            overlay.setVisible(TutorialStep.HIGHLIGHT_NONE.equals(highlight) ? false : false);
            overlay.setVisible(false);
        }
    }

    private Component resolveTarget(String highlight) {
        if (highlight == null || TutorialStep.HIGHLIGHT_NONE.equals(highlight)) return null;
        if (TutorialStep.HIGHLIGHT_TREE.equals(highlight))     return jbm.getJarTree();
        if (TutorialStep.HIGHLIGHT_CODELIST.equals(highlight)) return jbm.getCodeList();
        if (TutorialStep.HIGHLIGHT_MENUBAR.equals(highlight))  return jbm.getMyMenuBar();
        if (TutorialStep.HIGHLIGHT_TABS.equals(highlight))     return jbm.getTabbedPane();
        if (TutorialStep.HIGHLIGHT_INFOBAR.equals(highlight))  return jbm.getPageEndPanel();
        if (TutorialStep.HIGHLIGHT_SEARCH.equals(highlight))   return jbm.getSearchList();
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
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                TutorialDialog dlg = new TutorialDialog(jbm);
                dlg.setVisible(true);
            }
        });
    }

    public static void openIfFirstTime(JByteMod jbm) {
        if (!TutorialPrefs.hasSeenTutorial()) {
            open(jbm);
        }
    }
}
