package de.xbrowniecodez.jbytemod.jdwp;

import de.xbrowniecodez.jbytemod.jdwp.protocol.JdwpConnection;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

public class JdwpSessionManager {

    private static JdwpSessionManager instance;

    private volatile JdwpSession activeSession;
    private final CopyOnWriteArrayList<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    private JdwpSessionManager() {}

    public static synchronized JdwpSessionManager getInstance() {
        if (instance == null) {
            instance = new JdwpSessionManager();
        }
        return instance;
    }

    public JdwpSession connect(String host, int port) throws IOException {
        if (activeSession != null) {
            disconnect();
        }
        JdwpConnection conn = JdwpConnection.connect(host, port);
        JdwpSession session = new JdwpSession(conn);
        session.addListener(new JdwpSession.SessionListener() {
            @Override
            public void onBreakpointHit(JdwpModel.BreakpointHit hit) {}

            @Override
            public void onThreadEvent(long threadId, boolean started) {}

            @Override
            public void onVmDeath() {
                for (LifecycleListener l : lifecycleListeners) {
                    try { l.onSessionClosed("VM terminated"); } catch (Exception ignored) {}
                }
                activeSession = null;
            }

            @Override
            public void onDisconnected(Exception cause) {
                String msg = cause != null ? cause.getMessage() : "Connection lost";
                for (LifecycleListener l : lifecycleListeners) {
                    try { l.onSessionClosed(msg); } catch (Exception ignored) {}
                }
                activeSession = null;
            }
        });
        this.activeSession = session;
        for (LifecycleListener l : lifecycleListeners) {
            try { l.onSessionOpened(session); } catch (Exception ignored) {}
        }
        return session;
    }

    public void disconnect() {
        JdwpSession s = activeSession;
        if (s != null) {
            activeSession = null;
            s.close();
            for (LifecycleListener l : lifecycleListeners) {
                try { l.onSessionClosed("Disconnected by user"); } catch (Exception ignored) {}
            }
        }
    }

    public JdwpSession getActiveSession() {
        return activeSession;
    }

    public boolean isConnected() {
        return activeSession != null && activeSession.getConnection().isConnected();
    }

    public void addLifecycleListener(LifecycleListener l) {
        lifecycleListeners.add(l);
    }

    public void removeLifecycleListener(LifecycleListener l) {
        lifecycleListeners.remove(l);
    }

    public interface LifecycleListener {
        void onSessionOpened(JdwpSession session);
        void onSessionClosed(String reason);
    }
}
