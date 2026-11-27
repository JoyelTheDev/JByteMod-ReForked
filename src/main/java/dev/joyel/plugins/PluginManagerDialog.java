package dev.joyel.plugins;

import javax.swing.JDialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.function.Supplier;

public final class PluginManagerDialog extends JDialog {
    private static PluginManagerDialog current;

    private PluginManagerDialog(Frame owner, PluginHost host, PluginRepositoryService repository, PluginSettings settings,
                                Supplier<String> applicationVersion, boolean browse) {
        super(owner, "Plugin manager", false);
        PluginManagerPanel panel = new PluginManagerPanel(host, repository, settings, applicationVersion);
        if (browse) {
            panel.selectBrowseTab();
        }
        setContentPane(panel);
        setMinimumSize(new Dimension(760, 460));
        setSize(1020, 640);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    public static synchronized void open(Frame owner, PluginHost host, PluginRepositoryService repository,
                                         PluginSettings settings, Supplier<String> applicationVersion, boolean browse) {
        if (current != null && current.isDisplayable()) {
            current.toFront();
            current.requestFocus();
            return;
        }
        current = new PluginManagerDialog(owner, host, repository, settings, applicationVersion, browse);
        current.setVisible(true);
    }
}
