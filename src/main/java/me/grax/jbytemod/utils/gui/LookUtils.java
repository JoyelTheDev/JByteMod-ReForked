package me.grax.jbytemod.utils.gui;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import de.xbrowniecodez.jbytemod.Main;

import javax.swing.*;
import java.awt.*;

public class LookUtils {

    private static boolean defaultsInstalled;

    public static void setTheme() {
        Main.INSTANCE.getLogger().log("Setting Theme");
        installDefaults();
        install(isDark());
    }

    public static void changeTheme() {
        installDefaults();
        FlatAnimatedLafChange.showSnapshot();
        install(isDark());
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
        Main.INSTANCE.getJByteMod().getDecompilerPanel().setTheme();
    }

    private static boolean isDark() {
        return Main.INSTANCE.getJByteMod().getOptions().get("use_dark_theme").getBoolean();
    }

    private static void install(boolean dark) {
        boolean installed = dark ? FlatOneDarkIJTheme.setup() : FlatIntelliJLaf.setup();
        if (!installed) {
            Main.INSTANCE.getLogger().err("Failed to install " + (dark ? "dark" : "light") + " theme, falling back");
            FlatIntelliJLaf.setup();
        }
    }

    private static void installDefaults() {
        if (defaultsInstalled) {
            return;
        }
        defaultsInstalled = true;
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("CheckBox.arc", 6);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("MenuItem.selectionArc", 8);
        UIManager.put("PopupMenu.borderCornerRadius", 8);
        UIManager.put("Popup.dropShadowPainted", true);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", true);
        UIManager.put("TabbedPane.hasFullBorder", false);
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("SplitPaneDivider.style", "plain");
        UIManager.put("Tree.showDefaultIcons", false);
    }

    public static Color borderColor() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : Color.GRAY;
    }
}
