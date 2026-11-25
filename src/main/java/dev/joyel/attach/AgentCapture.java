package dev.joyel.attach;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AgentCapture {
    static final int OP_START = 0;
    static final int OP_STOP = 1;
    static final int OP_STATUS = 2;
    static final int OP_LIST = 3;
    static final int OP_FETCH = 4;
    static final int OP_CLEAR = 5;
    static final int OP_SCAN = 6;

    private static final int MAX_SITE_FRAMES = 16;
    private static final int FETCH_LIMIT = 10_000;
    private static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024;

    private static final Object LOCK = new Object();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Map<String, Entry> INDEX = new HashMap<>();
    private static final ThreadLocal<Boolean> REENTRY = new ThreadLocal<>();

    private static long nextId = 1;
    private static long sequence = 0;
    private static long totalBytes = 0;
    private static long dropped = 0;
    private static boolean overflow = false;
    private static long maxBytes = DEFAULT_MAX_BYTES;
    private static boolean onlyNoCodeSource = false;
    private static boolean includeLambdas = false;
    private static volatile Recorder recorder;

    private AgentCapture() {
    }

    static void handle(DataInputStream input, DataOutputStream output, Instrumentation instrumentation) throws Exception {
        int operation = input.readUnsignedByte();
        switch (operation) {
            case OP_START -> {
                boolean noCodeSource = input.readBoolean();
                boolean lambdas = input.readBoolean();
                int megabytes = input.readInt();
                start(instrumentation, noCodeSource, lambdas, megabytes);
                writeStatus(output);
            }
            case OP_STOP -> {
                stop(instrumentation);
                writeStatus(output);
            }
            case OP_STATUS -> writeStatus(output);
            case OP_LIST -> list(input.readLong(), output);
            case OP_FETCH -> fetch(input, output);
            case OP_CLEAR -> {
                clear();
                writeStatus(output);
            }
            case OP_SCAN -> scan(input.readBoolean(), instrumentation, output);
            default -> throw new IllegalArgumentException("Unknown capture operation: " + operation);
        }
    }

    static void shutdown(Instrumentation instrumentation) {
        stop(instrumentation);
        clear();
    }

    private static void start(Instrumentation instrumentation, boolean noCodeSource, boolean lambdas, int megabytes) {
        synchronized (LOCK) {
            onlyNoCodeSource = noCodeSource;
            includeLambdas = lambdas;
            maxBytes = megabytes <= 0 ? DEFAULT_MAX_BYTES : Math.min(megabytes, 2048) * 1024L * 1024L;
            if (recorder == null) {
                Recorder created = new Recorder();
                instrumentation.addTransformer(created, false);
                recorder = created;
            }
        }
    }

    private static void stop(Instrumentation instrumentation) {
        synchronized (LOCK) {
            Recorder current = recorder;
            recorder = null;
            if (current != null) {
                instrumentation.removeTransformer(current);
            }
        }
    }

    private static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
            INDEX.clear();
            totalBytes = 0;
            dropped = 0;
            overflow = false;
            sequence++;
        }
    }

    private static void writeStatus(DataOutputStream output) throws Exception {
        synchronized (LOCK) {
            output.writeByte(AgentServer.RESPONSE_OK);
            output.writeBoolean(recorder != null);
            output.writeInt(ENTRIES.size());
            output.writeLong(totalBytes);
            output.writeLong(maxBytes);
            output.writeLong(dropped);
            output.writeBoolean(overflow);
            output.writeLong(sequence);
            output.writeBoolean(onlyNoCodeSource);
            output.writeBoolean(includeLambdas);
            output.flush();
        }
    }

    private static void list(long since, DataOutputStream output) throws Exception {
        List<Entry> snapshot = new ArrayList<>();
        long latest;
        synchronized (LOCK) {
            latest = sequence;
            for (Entry entry : ENTRIES) {
                if (entry.changedAt > since) {
                    snapshot.add(entry);
                }
            }
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        output.writeByte(AgentServer.RESPONSE_OK);
        output.writeLong(latest);
        output.writeInt(snapshot.size());
        for (Entry entry : snapshot) {
            if (entry.sha256 == null) {
                digest.reset();
                entry.sha256 = HexFormat.of().formatHex(digest.digest(entry.bytes));
            }
            output.writeLong(entry.id);
            AgentServer.writeString(output, entry.name);
            AgentServer.writeString(output, entry.loaderId);
            AgentServer.writeString(output, entry.loaderName);
            AgentServer.writeString(output, entry.loaderType);
            AgentServer.writeNullableString(output, entry.origin);
            output.writeLong(entry.time);
            output.writeInt(entry.bytes.length);
            AgentServer.writeString(output, entry.source);
            AgentServer.writeNullableString(output, entry.thread);
            output.writeInt(entry.site.length);
            for (String frame : entry.site) {
                AgentServer.writeString(output, frame);
            }
            output.writeInt(entry.seen);
            AgentServer.writeString(output, entry.sha256);
        }
        output.flush();
    }

    private static void fetch(DataInputStream input, DataOutputStream output) throws Exception {
        int count = input.readInt();
        if (count < 0 || count > FETCH_LIMIT) {
            throw new IllegalArgumentException("Invalid fetch count: " + count);
        }
        long[] ids = new long[count];
        for (int index = 0; index < count; index++) {
            ids[index] = input.readLong();
        }
        Map<Long, byte[]> found = new LinkedHashMap<>();
        synchronized (LOCK) {
            Map<Long, Entry> byId = new HashMap<>();
            for (Entry entry : ENTRIES) {
                byId.put(entry.id, entry);
            }
            for (long id : ids) {
                Entry entry = byId.get(id);
                if (entry != null) {
                    found.put(id, entry.bytes);
                }
            }
        }
        output.writeByte(AgentServer.RESPONSE_OK);
        output.writeInt(count);
        for (long id : ids) {
            byte[] bytes = found.get(id);
            output.writeLong(id);
            output.writeBoolean(bytes != null);
            if (bytes != null) {
                output.writeInt(bytes.length);
                output.write(bytes);
            }
        }
        output.flush();
    }

    private static void scan(boolean lambdas, Instrumentation instrumentation, DataOutputStream output) throws Exception {
        int added = 0;
        if (instrumentation.isRetransformClassesSupported()) {
            ClassLoader agentLoader = AgentCapture.class.getClassLoader();
            List<Class<?>> candidates = new ArrayList<>();
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                if (isScanCandidate(type, agentLoader, lambdas, instrumentation)) {
                    candidates.add(type);
                }
            }
            Map<Class<?>, byte[]> captured = new IdentityHashMap<>();
            Set<Class<?>> targets = new HashSet<>(candidates);
            ClassFileTransformer collector = new ScanCollector(targets, captured);
            instrumentation.addTransformer(collector, true);
            try {
                for (int start = 0; start < candidates.size(); start += 50) {
                    List<Class<?>> batch = candidates.subList(start, Math.min(start + 50, candidates.size()));
                    try {
                        instrumentation.retransformClasses(batch.toArray(Class<?>[]::new));
                    } catch (Throwable failure) {
                        for (Class<?> type : batch) {
                            try {
                                instrumentation.retransformClasses(type);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            } finally {
                instrumentation.removeTransformer(collector);
            }
            for (Class<?> type : candidates) {
                byte[] bytes;
                synchronized (captured) {
                    bytes = captured.get(type);
                }
                if (bytes == null) {
                    continue;
                }
                ClassLoader loader = type.getClassLoader();
                if (add(loader, type.getName().replace('.', '/'), null, bytes, "scan", null, new String[0])) {
                    added++;
                }
            }
        }
        output.writeByte(AgentServer.RESPONSE_OK);
        output.writeInt(added);
        output.flush();
    }

    private static boolean isScanCandidate(Class<?> type, ClassLoader agentLoader, boolean lambdas,
                                           Instrumentation instrumentation) {
        if (type.isArray() || type.isHidden() || type.getClassLoader() == agentLoader) {
            return false;
        }
        if (!instrumentation.isModifiableClass(type)) {
            return false;
        }
        String name = type.getName().replace('.', '/');
        if (isJdkName(name) || (!lambdas && name.contains("$$Lambda"))) {
            return false;
        }
        try {
            ProtectionDomain domain = type.getProtectionDomain();
            CodeSource source = domain == null ? null : domain.getCodeSource();
            return source == null || source.getLocation() == null;
        } catch (Throwable failure) {
            return false;
        }
    }

    private static boolean isJdkName(String name) {
        return name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("sun/")
                || name.startsWith("com/sun/") || name.startsWith("jdk/");
    }

    private static boolean add(ClassLoader loader, String name, String origin, byte[] bytes, String source,
                               String thread, String[] site) {
        String loaderId = loader == null ? "bootstrap" : loader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(loader));
        String loaderName = loader == null ? "Bootstrap ClassLoader"
                : (loader.getName() == null ? loader.getClass().getSimpleName() : loader.getName());
        String loaderType = loader == null ? "<bootstrap>" : loader.getClass().getName();
        String key = loaderId + "|" + name + "|" + bytes.length + "|" + Arrays.hashCode(bytes);
        synchronized (LOCK) {
            Entry existing = INDEX.get(key);
            if (existing != null) {
                existing.seen++;
                existing.changedAt = ++sequence;
                return false;
            }
            if (totalBytes + bytes.length > maxBytes) {
                dropped++;
                overflow = true;
                sequence++;
                return false;
            }
            Entry entry = new Entry(nextId++, name, loaderId, loaderName, loaderType, origin,
                    System.currentTimeMillis(), bytes, source, thread, site);
            entry.changedAt = ++sequence;
            ENTRIES.add(entry);
            INDEX.put(key, entry);
            totalBytes += bytes.length;
            return true;
        }
    }

    static String parseClassName(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0xCAFEBABE) {
                return null;
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            String[] utf = new String[count];
            int[] classIndex = new int[count];
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf[index] = in.readUTF();
                    case 3, 4 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        index++;
                    }
                    case 7 -> classIndex[index] = in.readUnsignedShort();
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 9, 10, 11, 12, 17, 18 -> in.readInt();
                    case 15 -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    default -> {
                        return null;
                    }
                }
            }
            in.readUnsignedShort();
            int thisClass = in.readUnsignedShort();
            return utf[classIndex[thisClass]];
        } catch (Throwable failure) {
            return null;
        }
    }

    private static String[] captureSite() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        List<String> frames = new ArrayList<>();
        boolean skipping = true;
        for (StackTraceElement element : stack) {
            String owner = element.getClassName();
            if (skipping && (owner.equals("java.lang.Thread") || owner.startsWith("java.lang.ClassLoader")
                    || owner.startsWith("java.security.SecureClassLoader") || owner.startsWith("jdk.internal.")
                    || owner.startsWith("sun.instrument.") || owner.startsWith("java.lang.instrument.")
                    || owner.startsWith("dev.joyel.attach."))) {
                continue;
            }
            skipping = false;
            frames.add(element.toString());
            if (frames.size() >= MAX_SITE_FRAMES) {
                break;
            }
        }
        return frames.toArray(String[]::new);
    }

    private static final class Entry {
        final long id;
        final String name;
        final String loaderId;
        final String loaderName;
        final String loaderType;
        final String origin;
        final long time;
        final byte[] bytes;
        final String source;
        final String thread;
        final String[] site;
        int seen = 1;
        long changedAt;
        String sha256;

        Entry(long id, String name, String loaderId, String loaderName, String loaderType, String origin, long time,
              byte[] bytes, String source, String thread, String[] site) {
            this.id = id;
            this.name = name;
            this.loaderId = loaderId;
            this.loaderName = loaderName;
            this.loaderType = loaderType;
            this.origin = origin;
            this.time = time;
            this.bytes = bytes;
            this.source = source;
            this.thread = thread;
            this.site = site;
        }
    }

    private static final class ScanCollector implements ClassFileTransformer {
        private final Set<Class<?>> targets;
        private final Map<Class<?>, byte[]> captured;

        ScanCollector(Set<Class<?>> targets, Map<Class<?>, byte[]> captured) {
            this.targets = targets;
            this.captured = captured;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (classBeingRedefined != null && targets.contains(classBeingRedefined)) {
                synchronized (captured) {
                    captured.put(classBeingRedefined, classfileBuffer.clone());
                }
            }
            return null;
        }
    }

    private static final class Recorder implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (classBeingRedefined != null || classfileBuffer == null || REENTRY.get() != null) {
                return null;
            }
            REENTRY.set(Boolean.TRUE);
            try {
                if (loader != null && loader == AgentCapture.class.getClassLoader()) {
                    return null;
                }
                String name = className != null ? className : parseClassName(classfileBuffer);
                if (name == null || isJdkName(name)) {
                    return null;
                }
                if (!includeLambdas && name.contains("$$Lambda")) {
                    return null;
                }
                String origin = null;
                if (protectionDomain != null) {
                    CodeSource source = protectionDomain.getCodeSource();
                    URL location = source == null ? null : source.getLocation();
                    origin = location == null ? null : location.toString();
                }
                if (onlyNoCodeSource && origin != null) {
                    return null;
                }
                add(loader, name, origin, classfileBuffer.clone(), "define",
                        Thread.currentThread().getName(), captureSite());
            } catch (Throwable ignored) {
            } finally {
                REENTRY.remove();
            }
            return null;
        }
    }
}
