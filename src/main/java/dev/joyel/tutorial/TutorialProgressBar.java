package dev.joyel.tutorial;

import javax.swing.*;
import java.awt.*;

public final class TutorialProgressBar extends JComponent {

    private static final int DOT_SIZE   = 9;
    private static final int DOT_GAP    = 6;
    private static final Color ACTIVE   = new Color(255, 180, 0);
    private static final Color DONE     = new Color(100, 180, 100);
    private static final Color INACTIVE = new Color(80, 80, 80);

    private int total   = 1;
    private int current = 0;

    public void update(int current, int total) {
        this.current = current;
        this.total   = total;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int width = total * (DOT_SIZE + DOT_GAP) - DOT_GAP;
        return new Dimension(Math.max(width, 20), DOT_SIZE + 4);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int totalWidth = total * (DOT_SIZE + DOT_GAP) - DOT_GAP;
        int startX = (getWidth() - totalWidth) / 2;
        int y = (getHeight() - DOT_SIZE) / 2;

        for (int i = 0; i < total; i++) {
            int x = startX + i * (DOT_SIZE + DOT_GAP);
            if (i < current) {
                g2.setColor(DONE);
            } else if (i == current) {
                g2.setColor(ACTIVE);
            } else {
                g2.setColor(INACTIVE);
            }
            g2.fillOval(x, y, DOT_SIZE, DOT_SIZE);
        }

        g2.dispose();
    }
}
