package dev.joyel.theme;

import java.awt.Color;

public final class ThemeToken {
    private final String key;
    private final String label;
    private final ThemeColorCategory category;
    private Color color;

    public ThemeToken(String key, String label, ThemeColorCategory category, Color defaultColor) {
        this.key = key;
        this.label = label;
        this.category = category;
        this.color = defaultColor;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public ThemeColorCategory getCategory() {
        return category;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public String toHex() {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public String toHexWithAlpha() {
        return String.format("#%02x%02x%02x%02x",
                color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }
}