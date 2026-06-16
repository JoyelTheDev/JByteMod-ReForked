package de.xbrowniecodez.jbytemod.jdwp.task;

import de.xbrowniecodez.jbytemod.jdwp.JdwpModel;
import de.xbrowniecodez.jbytemod.jdwp.JdwpSession;
import de.xbrowniecodez.jbytemod.jdwp.ui.JdwpPanel;

import javax.swing.*;
import java.util.List;

public class JdwpRefreshClassesTask extends SwingWorker<List<JdwpModel.RemoteClass>, Void> {

    private final JdwpSession session;
    private final JdwpPanel panel;

    public JdwpRefreshClassesTask(JdwpSession session, JdwpPanel panel) {
        this.session = session;
        this.panel = panel;
    }

    @Override
    protected List<JdwpModel.RemoteClass> doInBackground() throws Exception {
        return session.getAllClasses();
    }

    @Override
    protected void done() {
        try {
            List<JdwpModel.RemoteClass> classes = get();
            panel.updateClassList(classes);
        } catch (Exception e) {
            panel.showError("Failed to refresh classes: " + e.getMessage());
        }
    }
}
