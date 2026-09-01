package me.grax.jbytemod.utils;

import de.xbrowniecodez.jbytemod.Main;

import javax.swing.*;
import java.awt.*;

public class ThemeChanges {

    public static void changeDefaultFont(Font f) {
        String[] keys = {
            "Button.font", "ToggleButton.font", "RadioButton.font", "CheckBox.font",
            "ColorChooser.font", "ComboBox.font", "Label.font", "List.font",
            "MenuBar.font", "MenuItem.font", "RadioButtonMenuItem.font", "CheckBoxMenuItem.font",
            "Menu.font", "PopupMenu.font", "OptionPane.font", "Panel.font",
            "ProgressBar.font", "ScrollPane.font", "Viewport.font", "TabbedPane.font",
            "Table.font", "TableHeader.font", "TextField.font", "PasswordField.font",
            "TextArea.font", "TextPane.font", "EditorPane.font", "TitledBorder.font",
            "ToolBar.font", "ToolTip.font", "Tree.font"
        };
        for (String key : keys) {
            UIManager.put(key, f);
        }
    }
}
