package dev.joyel.tutorial;

import javax.swing.*;
import java.awt.*;

public final class TutorialOverlay extends JComponent {

    private static final Color DIM_COLOR    = new Color(0, 0, 0, 130);
    private static final Color RING_COLOR   = new Color(255, 200, 0, 200);
    private static final int   RING_PAD     = 4;
    private static final int   RING_ARC     = 8;
    private static final int   RING_THICK   = 3;

    private Component target = null;

    public void setTarget(Component c) {
        this.target = c;
        repaint();
    }

    public void clearTarget() {
        this.target = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (target == null) {
            g2.setColor(DIM_COLOR);
            g2.fillRect(0, 0, w, h);
        } else {
            Point p = SwingUtilities.convertPoint(target.getParent(), target.getLocation(), this);
            int tx = p.x - RING_PAD;
            int ty = p.y - RING_PAD;
            int tw = target.getWidth()  + RING_PAD * 2;
            int th = target.getHeight() + RING_PAD * 2;

            g2.setColor(DIM_COLOR);
            g2.fillRect(0, 0, w, ty);
            g2.fillRect(0, ty + th, w, h - ty - th);
            g2.fillRect(0, ty, tx, th);
            g2.fillRect(tx + tw, ty, w - tx - tw, th);

            g2.setColor(RING_COLOR);
            g2.setStroke(new BasicStroke(RING_THICK));
            g2.drawRoundRect(tx, ty, tw, th, RING_ARC, RING_ARC);
        }

        g2.dispose();
    }
}
