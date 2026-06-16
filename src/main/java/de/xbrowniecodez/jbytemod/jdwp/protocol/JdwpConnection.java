package de.xbrowniecodez.jbytemod.jdwp.protocol;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JdwpConnection implements Closeable {

    private static final String HANDSHAKE = "JDWP-Handshake";
    private static final int SOCKET_TIMEOUT_MS = 30_000;

    private final Socket socket;
    private final OutputStream out;
    private final InputStream in;
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, CompletableFuture<JdwpPacket>> pending = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<EventListener> eventListeners = new CopyOnWriteArrayList<>();

    private volatile boolean running = true;
    private Thread readerThread;

    private int fieldIdSize = 8;
    private int methodIdSize = 8;
    private int objectIdSize = 8;
    private int referenceTypeIdSize = 8;
    private int frameIdSize = 8;

    private JdwpConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        this.out = socket.getOutputStream();
        this.in = socket.getInputStream();
    }

    public static JdwpConnection connect(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);
        JdwpConnection conn = new JdwpConnection(socket);
        conn.performHandshake();
        conn.startReader();
        conn.fetchIdSizes();
        return conn;
    }

    private void performHandshake() throws IOException {
        byte[] hs = HANDSHAKE.getBytes(StandardCharsets.US_ASCII);
        out.write(hs);
        out.flush();
        byte[] resp = new byte[hs.length];
        int read = 0;
        while (read < resp.length) {
            int r = in.read(resp, read, resp.length - read);
            if (r < 0) throw new IOException("Connection closed during JDWP handshake");
            read += r;
        }
        if (!HANDSHAKE.equals(new String(resp, StandardCharsets.US_ASCII))) {
            throw new IOException("JDWP handshake failed: unexpected response");
        }
    }

    private void startReader() {
        readerThread = new Thread(this::readerLoop, "jdwp-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readerLoop() {
        while (running) {
            try {
                JdwpPacket pkt = JdwpPacket.readFrom(in);
                if (pkt.isReply()) {
                    CompletableFuture<JdwpPacket> future = pending.remove(pkt.id);
                    if (future != null) {
                        future.complete(pkt);
                    }
                } else if (pkt.commandSet == JdwpCommands.CS_EVENT && pkt.command == JdwpCommands.EV_COMPOSITE) {
                    dispatchEvent(pkt);
                }
            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                if (running) {
                    fireDisconnected(e);
                }
                break;
            }
        }
    }

    private void dispatchEvent(JdwpPacket pkt) {
        for (EventListener l : eventListeners) {
            try {
                l.onEvent(pkt);
            } catch (Exception ignored) {}
        }
    }

    private void fireDisconnected(Exception cause) {
        for (EventListener l : eventListeners) {
            try {
                l.onDisconnected(cause);
            } catch (Exception ignored) {}
        }
    }

    public JdwpPacket send(int commandSet, int command, byte[] data) throws IOException, InterruptedException {
        int id = idCounter.getAndIncrement();
        JdwpPacket pkt = JdwpPacket.command(id, commandSet, command, data);
        CompletableFuture<JdwpPacket> future = new CompletableFuture<>();
        pending.put(id, future);
        synchronized (out) {
            out.write(pkt.toBytes());
            out.flush();
        }
        try {
            return future.get(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new IOException("JDWP command timed out (cs=" + commandSet + " cmd=" + command + ")", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("JDWP future error", cause);
        }
    }

    public JdwpPacket send(int commandSet, int command) throws IOException, InterruptedException {
        return send(commandSet, command, null);
    }

    private void fetchIdSizes() throws IOException {
        try {
            JdwpPacket reply = send(JdwpCommands.CS_VIRTUAL_MACHINE, JdwpCommands.VM_ID_SIZES);
            if (!reply.isError()) {
                JdwpPacket.DataReader r = reply.reader();
                fieldIdSize = r.readInt();
                methodIdSize = r.readInt();
                objectIdSize = r.readInt();
                referenceTypeIdSize = r.readInt();
                frameIdSize = r.readInt();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void addEventListener(EventListener listener) {
        eventListeners.add(listener);
    }

    public void removeEventListener(EventListener listener) {
        eventListeners.remove(listener);
    }

    public int getFieldIdSize() { return fieldIdSize; }
    public int getMethodIdSize() { return methodIdSize; }
    public int getObjectIdSize() { return objectIdSize; }
    public int getReferenceTypeIdSize() { return referenceTypeIdSize; }
    public int getFrameIdSize() { return frameIdSize; }

    public boolean isConnected() {
        return running && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() {
        running = false;
        try { socket.close(); } catch (IOException ignored) {}
        if (readerThread != null) {
            readerThread.interrupt();
        }
        for (CompletableFuture<JdwpPacket> f : pending.values()) {
            f.completeExceptionally(new IOException("Connection closed"));
        }
        pending.clear();
    }

    public interface EventListener {
        void onEvent(JdwpPacket eventPacket);
        void onDisconnected(Exception cause);
    }
}
