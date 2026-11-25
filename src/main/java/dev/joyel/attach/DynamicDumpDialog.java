package dev.joyel.attach;

import javax.swing.JDialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class DynamicDumpDialog extends JDialog {
   
    private static DynamicDumpDialog current;
    private final DumpPanel panel;
    private final RemoteJarArchive archive;

    private DynamicDumpDialog(Frame owner, RemoteJarArchive archive) {
        super(owner, "Dynamic class dump - PID " + archive.getProcessId(), false);
        this.archive = archive;
        this.panel = new DumpPanel(archive);
        setContentPane(panel);
        setMinimumSize(new Dimension(760, 460));
        setSize(1100, 680);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                panel.shutdown();
                synchronized (DynamicDumpDialog.class) {
                    if (current == DynamicDumpDialog.this) {
                        current = null;
                    }
                }
            }
        });
    }

    public static synchronized void open(Frame owner, RemoteJarArchive archive) {
        if (current != null && current.isDisplayable() && current.archive == archive) {
            current.toFront();
            current.requestFocus();
            return;
        }
        if (current != null) {
            current.dispose();
        }
        current = new DynamicDumpDialog(owner, archive);
        current.setVisible(true);
    }
}
