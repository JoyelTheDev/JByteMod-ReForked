package dev.joyel.theme;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JByteTheme {
    private String name;
    private final boolean builtIn;
    private final Map<String, Color> colors = new LinkedHashMap<String, Color>();

    public JByteTheme(String name, boolean builtIn) {
        this.name = name;
        this.builtIn = builtIn;
        for (ThemeToken token : JBytePalette.all()) {
            colors.put(token.getKey(), token.getColor());
        }
    }

    private JByteTheme(String name, boolean builtIn, Map<String, Color> colors) {
        this.name = name;
        this.builtIn = builtIn;
        this.colors.putAll(colors);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isBuiltIn() { return builtIn; }

    public Color getColor(String key) {
        Color c = colors.get(key);
        if (c != null) return c;
        ThemeToken t = JBytePalette.get(key);
        return t != null ? t.getColor() : Color.GRAY;
    }

    public void setColor(String key, Color color) {
        colors.put(key, color);
    }

    public Map<String, Color> getColors() {
        return colors;
    }

    public JByteTheme deepCopy(String newName) {
        return new JByteTheme(newName, false, new LinkedHashMap<String, Color>(colors));
    }

    public void applyToPalette() {
        for (ThemeToken token : JBytePalette.all()) {
            Color c = colors.get(token.getKey());
            if (c != null) token.setColor(c);
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(escapeProp(name)).append("\n");
        for (Map.Entry<String, Color> e : colors.entrySet()) {
            Color c = e.getValue();
            sb.append(escapeProp(e.getKey())).append("=")
              .append(String.format("#%02x%02x%02x%02x",
                      c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha()))
              .append("\n");
        }
        return sb.toString();
    }

    public static JByteTheme deserialize(String data) {
        Map<String, Color> colors = new LinkedHashMap<String, Color>();
        String name = "Unnamed";
        for (String line : data.split("\\r?\\n|\\r")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (key.equals("name")) { name = val; continue; }
            Color c = parseColor(val);
            if (c != null) colors.put(key, c);
        }
        JByteTheme theme = new JByteTheme(name, false);
        theme.colors.clear();
        for (ThemeToken token : JBytePalette.all()) {
            Color c = colors.get(token.getKey());
            theme.colors.put(token.getKey(), c != null ? c : token.getColor());
        }
        return theme;
    }

    private static Color parseColor(String hex) {
        try {
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
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private static String escapeProp(String s) {
        return s.replace("\\", "\\\\").replace("=", "\\=").replace("\n", "\\n");
    }
}
