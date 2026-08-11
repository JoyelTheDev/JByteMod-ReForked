package dev.joyel.theme.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class ColorSwatch extends JComponent {
    private Color color;
    private boolean selected;

    public ColorSwatch(Color color) {
        this.color = color;
        setPreferredSize(new Dimension(36, 20));
        setOpaque(false);
    }

    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    public Color getColor() {
        return color;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int arc = 4;
        g2.setColor(color != null ? color : Color.GRAY);
        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);
        g2.setColor(selected ? Color.WHITE : new Color(0, 0, 0, 80));
        g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc);
        g2.dispose();
    }
}
