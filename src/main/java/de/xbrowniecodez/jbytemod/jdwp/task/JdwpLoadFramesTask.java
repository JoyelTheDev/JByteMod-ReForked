package de.xbrowniecodez.jbytemod.jdwp.task;

import de.xbrowniecodez.jbytemod.jdwp.JdwpModel;
import de.xbrowniecodez.jbytemod.jdwp.JdwpSession;
import de.xbrowniecodez.jbytemod.jdwp.ui.JdwpPanel;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class JdwpLoadFramesTask extends SwingWorker<List<JdwpModel.StackFrame>, Void> {

    private final JdwpSession session;
    private final JdwpModel.RemoteThread thread;
    private final JdwpPanel panel;

    public JdwpLoadFramesTask(JdwpSession session, JdwpModel.RemoteThread thread, JdwpPanel panel) {
        this.session = session;
        this.thread = thread;
        this.panel = panel;
    }

    @Override
    protected List<JdwpModel.StackFrame> doInBackground() throws Exception {
        List<JdwpModel.StackFrame> frames = session.getFrames(thread.threadId);
        for (JdwpModel.StackFrame frame : frames) {
            try {
                frame.locals = session.getFrameLocals(frame);
            } catch (Exception ignored) {
                frame.locals = new ArrayList<>();
            }
        }
        return frames;
    }

    @Override
    protected void done() {
        try {
            List<JdwpModel.StackFrame> frames = get();
            panel.updateFrames(thread, frames);
        } catch (Exception e) {
            panel.showError("Failed to load frames: " + e.getMessage());
        }
    }
}
