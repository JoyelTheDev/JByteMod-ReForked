package me.grax.jbytemod.utils.gui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

public final class Icons {

    private static final String BASE = "resources/toolbar/svg/";

    private Icons() {
    }

    public static FlatSVGIcon svg(String name, int size) {
        return new FlatSVGIcon(BASE + name + ".svg", size, size, Icons.class.getClassLoader());
    }
}
