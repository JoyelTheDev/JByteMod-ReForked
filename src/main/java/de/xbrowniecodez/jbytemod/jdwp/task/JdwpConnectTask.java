package de.xbrowniecodez.jbytemod.jdwp.task;

import de.xbrowniecodez.jbytemod.jdwp.JdwpModel;
import de.xbrowniecodez.jbytemod.jdwp.JdwpSession;
import de.xbrowniecodez.jbytemod.jdwp.JdwpSessionManager;
import de.xbrowniecodez.jbytemod.jdwp.ui.JdwpPanel;
import me.grax.jbytemod.ui.PageEndPanel;

import javax.swing.*;
import java.util.List;

public class JdwpConnectTask extends SwingWorker<JdwpSession, String> {

    private final String host;
    private final int port;
    private final JdwpPanel panel;
    private final PageEndPanel progressPanel;

    public JdwpConnectTask(String host, int port, JdwpPanel panel, PageEndPanel progressPanel) {
        this.host = host;
        this.port = port;
        this.panel = panel;
        this.progressPanel = progressPanel;
    }

    @Override
    protected JdwpSession doInBackground() throws Exception {
        publish("Connecting to " + host + ":" + port + " ...");
        setProgress(10);
        JdwpSession session = JdwpSessionManager.getInstance().connect(host, port);
        setProgress(40);
        publish("Handshake OK, fetching VM info...");
        JdwpModel.VmInfo info = session.getVmInfo();
        setProgress(70);
        publish("Loading classes from " + info.vmName + "...");
        session.getAllClasses();
        setProgress(100);
        return session;
    }

    @Override
    protected void process(List<String> chunks) {
        String last = chunks.get(chunks.size() - 1);
        if (progressPanel != null) progressPanel.setTip(last);
    }

    @Override
    protected void done() {
        try {
            JdwpSession session = get();
            panel.onConnected(session);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            panel.onConnectFailed(cause.getMessage());
        } finally {
            if (progressPanel != null) progressPanel.setValue(100);
        }
    }
}
