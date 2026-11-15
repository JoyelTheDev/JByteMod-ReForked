package dev.joyel.editor;

import de.xbrowniecodez.jbytemod.Main;
import me.grax.jbytemod.res.Option;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EditorExtras {

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private EditorExtras() {
    }

    public static boolean minimapEnabled() {
        return flag("show_minimap");
    }

    public static boolean breadcrumbsEnabled() {
        return flag("show_breadcrumbs");
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void fireChanged() {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    private static boolean flag(String name) {
        Option option = Main.INSTANCE.getJByteMod().getOptions().get(name);
        return option == null || option.getBoolean();
    }

    static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    static Color mix(Color a, Color b, float t) {
        float u = 1f - t;
        return new Color(
                Math.round(a.getRed() * u + b.getRed() * t),
                Math.round(a.getGreen() * u + b.getGreen() * t),
                Math.round(a.getBlue() * u + b.getBlue() * t));
    }

    static Color accent() {
        for (String key : new String[]{"Component.accentColor", "Component.focusColor", "Button.default.background"}) {
            Color color = javax.swing.UIManager.getColor(key);
            if (color != null) {
                return color;
            }
        }
        return new Color(0x4B9EFF);
    }
}
