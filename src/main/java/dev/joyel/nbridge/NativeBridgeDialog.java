package dev.joyel.nbridge;

import javax.swing.JDialog;
import java.awt.Dimension;
import java.awt.Frame;

public final class NativeBridgeDialog extends JDialog {
    private static NativeBridgeDialog current;

    private NativeBridgeDialog(Frame owner, NativeHost host) {
        super(owner, "Native bridge", false);
        setContentPane(new NativeBridgePanel(host));
        setMinimumSize(new Dimension(820, 500));
        setSize(1180, 720);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    public static synchronized void open(Frame owner, NativeHost host) {
        if (current != null && current.isDisplayable()) {
            current.dispose();
        }
        current = new NativeBridgeDialog(owner, host);
        current.setVisible(true);
    }
}
