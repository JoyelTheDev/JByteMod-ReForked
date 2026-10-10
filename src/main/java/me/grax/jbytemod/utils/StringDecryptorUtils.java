package me.grax.jbytemod.utils;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.Opcodes.*;

@SuppressWarnings("java:S3011")
public class StringDecryptorUtils {

    private static final long CALL_TIMEOUT_MS = 5000;

    private static final class LoadedDecryptor implements AutoCloseable {
        private final Class<?> clazz;
        private final URLClassLoader loader;
        private final java.io.File tempJar;

        private LoadedDecryptor(Class<?> clazz, URLClassLoader loader, java.io.File tempJar) {
            this.clazz = clazz;
            this.loader = loader;
            this.tempJar = tempJar;
        }

        @Override
        public void close() {
            try {
                loader.close();
            } catch (java.io.IOException e) {
                de.xbrowniecodez.jbytemod.Main.INSTANCE.getLogger().warn("Decryptor class loader failed to close: " + e);
            }
            tempJar.delete();
        }
    }

    private static final class TimedInvoker implements AutoCloseable {
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "JByteMod-StringDecryptor");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicBoolean timedOut = new AtomicBoolean();

        Object invoke(Method method, Object[] args) throws Exception {
            if (timedOut.get()) {
                throw new IllegalStateException("decryptor aborted after a timeout");
            }
            Callable<Object> call = () -> method.invoke(null, args);
            Future<Object> future = executor.submit(call);
            try {
                return future.get(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                timedOut.set(true);
                future.cancel(true);
                de.xbrowniecodez.jbytemod.Main.INSTANCE.getLogger().warn("String decryptor call exceeded " + CALL_TIMEOUT_MS + " ms, aborting decryption");
                throw e;
            }
        }

        boolean hasTimedOut() {
            return timedOut.get();
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

    public static int decryptStrings(Map<String, ClassNode> classes, String targetOwner, String targetName, String targetDesc, byte[] jarBytes) {
        LoadedDecryptor loaded = loadDecryptorClass(targetOwner, jarBytes);
        if (loaded == null) {
            return -1;
        }
        try (LoadedDecryptor decryptor = loaded; TimedInvoker invoker = new TimedInvoker()) {
            Method decryptMethod = resolveMethod(decryptor.clazz, targetName, targetDesc);
            if (decryptMethod == null) {
                return -1;
            }
            return decryptAll(classes, targetOwner, targetName, targetDesc, decryptMethod, invoker);
        }
    }

    private static int decryptAll(Map<String, ClassNode> classes, String targetOwner, String targetName, String targetDesc,
                                  Method decryptMethod, TimedInvoker invoker) {
        AtomicInteger count = new AtomicInteger();

        String normalizedOwner = targetOwner.replace('.', '/');

        classes.values().forEach(classNode ->
            classNode.methods.forEach(methodNode -> {
                if (invoker.hasTimedOut() || methodNode.instructions.getFirst() == null) {
                    return;
                }

                AnalyzerResult result;
                try {
                    result = MethodAnalyzer.analyze(classNode, methodNode);
                } catch (Throwable t) {
                    return;
                }

                Map<AbstractInsnNode, InsnList> replacements = new HashMap<>();

                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode ain = methodNode.instructions.get(i);

                    if (ain.getOpcode() != INVOKESTATIC) {
                        continue;
                    }

                    MethodInsnNode min = (MethodInsnNode) ain;
                    if (!min.owner.equals(normalizedOwner) || !min.name.equals(targetName) || !min.desc.equals(targetDesc)) {
                        continue;
                    }

                    List<Frame> frames = result.getFrames().get(ain);
                    if (frames == null || frames.isEmpty()) {
                        continue;
                    }

                    Set<String> results = new HashSet<>();
                    boolean allConstant = true;

                    for (Frame frame0 : frames) {
                        if (!(frame0 instanceof MethodFrame)) {
                            allConstant = false;
                            break;
                        }
                        MethodFrame mf = (MethodFrame) frame0;
                        List<Frame> args = mf.getArgs();
                        if (!allArgsConstant(args)) {
                            allConstant = false;
                            break;
                        }
                        Object[] argValues = extractArgValues(args, targetDesc);
                        if (argValues == null) {
                            allConstant = false;
                            break;
                        }
                        try {
                            Object decrypted = invoker.invoke(decryptMethod, argValues);
                            if (decrypted instanceof String) {
                                results.add((String) decrypted);
                            } else {
                                allConstant = false;
                            }
                        } catch (Throwable t) {
                            allConstant = false;
                        }
                    }

                    if (allConstant && results.size() == 1) {
                        String plain = results.iterator().next();
                        InsnList replacement = new InsnList();
                        int argCount = Type.getArgumentTypes(targetDesc).length;
                        for (int j = 0; j < argCount; j++) {
                            replacement.add(new InsnNode(POP));
                        }
                        replacement.add(new LdcInsnNode(plain));
                        replacements.put(ain, replacement);
                        count.incrementAndGet();
                    }
                }

                replacements.forEach((node, insns) -> {
                    methodNode.instructions.insertBefore(node, insns);
                    methodNode.instructions.remove(node);
                });
            })
        );

        return count.get();
    }

    private static boolean allArgsConstant(List<Frame> args) {
        for (Frame f : args) {
            if (!(f instanceof LdcFrame)) {
                return false;
            }
        }
        return true;
    }

    private static Object[] extractArgValues(List<Frame> args, String desc) {
        Type[] argTypes = Type.getArgumentTypes(desc);
        if (argTypes.length != args.size()) {
            return null;
        }
        Object[] values = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            Object cst = ((LdcFrame) args.get(i)).getConstant();
            values[i] = coerce(cst, argTypes[i]);
            if (values[i] == null && cst != null) {
                return null;
            }
        }
        return values;
    }

    private static Object coerce(Object cst, Type target) {
        if (cst == null) {
            return null;
        }
        switch (target.getSort()) {
            case Type.INT:
                if (cst instanceof Number) return ((Number) cst).intValue();
                break;
            case Type.LONG:
                if (cst instanceof Number) return ((Number) cst).longValue();
                break;
            case Type.FLOAT:
                if (cst instanceof Number) return ((Number) cst).floatValue();
                break;
            case Type.DOUBLE:
                if (cst instanceof Number) return ((Number) cst).doubleValue();
                break;
            case Type.OBJECT:
                return cst;
        }
        return null;
    }

    private static LoadedDecryptor loadDecryptorClass(String owner, byte[] jarBytes) {
        java.io.File tempJar = null;
        URLClassLoader loader = null;
        try {
            tempJar = java.io.File.createTempFile("jbm_decrypt_", ".jar");
            tempJar.deleteOnExit();
            java.nio.file.Files.write(tempJar.toPath(), jarBytes);
            loader = new URLClassLoader(new URL[]{tempJar.toURI().toURL()}, ClassLoader.getSystemClassLoader());
            Class<?> clazz = Class.forName(owner.replace('/', '.'), true, loader);
            return new LoadedDecryptor(clazz, loader, tempJar);
        } catch (Throwable t) {
            if (loader != null) {
                try {
                    loader.close();
                } catch (java.io.IOException e) {
                    de.xbrowniecodez.jbytemod.Main.INSTANCE.getLogger().warn("Decryptor class loader failed to close: " + e);
                }
            }
            if (tempJar != null) {
                tempJar.delete();
            }
            return null;
        }
    }

    private static Method resolveMethod(Class<?> clazz, String name, String desc) {
        Type[] argTypes = Type.getArgumentTypes(desc);
        Class<?>[] paramClasses = new Class<?>[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            paramClasses[i] = asmTypeToClass(argTypes[i]);
            if (paramClasses[i] == null) {
                return null;
            }
        }
        try {
            Method m = clazz.getDeclaredMethod(name, paramClasses);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == argTypes.length) {
                    m.setAccessible(true);
                    return m;
                }
            }
            return null;
        }
    }

    private static Class<?> asmTypeToClass(Type t) {
        switch (t.getSort()) {
            case Type.INT:     return int.class;
            case Type.LONG:    return long.class;
            case Type.FLOAT:   return float.class;
            case Type.DOUBLE:  return double.class;
            case Type.BOOLEAN: return boolean.class;
            case Type.BYTE:    return byte.class;
            case Type.CHAR:    return char.class;
            case Type.SHORT:   return short.class;
            case Type.OBJECT:
                switch (t.getClassName()) {
                    case "java.lang.String":  return String.class;
                    case "java.lang.Object":  return Object.class;
                    case "java.lang.Integer": return Integer.class;
                    case "java.lang.Long":    return Long.class;
                    default:
                        try {
                            return Class.forName(t.getClassName());
                        } catch (ClassNotFoundException e) {
                            return null;
                        }
                }
            default:
                return null;
        }
    }

    public static List<String> findCandidateDecryptors(Map<String, ClassNode> classes) {
        List<String> candidates = new ArrayList<>();
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & ACC_STATIC) == 0) {
                    continue;
                }
                Type returnType = Type.getReturnType(mn.desc);
                if (returnType.getSort() != Type.OBJECT || !returnType.getClassName().equals("java.lang.String")) {
                    continue;
                }
                Type[] args = Type.getArgumentTypes(mn.desc);
                if (args.length == 0) {
                    continue;
                }
                boolean hasStringOrNumericArg = false;
                for (Type arg : args) {
                    if (arg.getSort() == Type.OBJECT && arg.getClassName().equals("java.lang.String")) {
                        hasStringOrNumericArg = true;
                        break;
                    }
                    if (arg.getSort() == Type.INT || arg.getSort() == Type.LONG) {
                        hasStringOrNumericArg = true;
                        break;
                    }
                }
                if (!hasStringOrNumericArg) {
                    continue;
                }
                if (hasDecryptionHeuristics(mn)) {
                    candidates.add(cn.name + "." + mn.name + mn.desc);
                }
            }
        }
        return candidates;
    }

    private static boolean hasDecryptionHeuristics(MethodNode mn) {
        int charOps = 0;
        int xorOps = 0;
        int arrayOps = 0;
        boolean hasNewCharArray = false;
        boolean hasStringConstruct = false;

        for (AbstractInsnNode ain : mn.instructions.toArray()) {
            int op = ain.getOpcode();
            if (op == IXOR || op == LXOR) xorOps++;
            if (op == CALOAD || op == CASTORE) charOps++;
            if (op == NEWARRAY || op == ANEWARRAY) arrayOps++;
            if (op == NEWARRAY) {
                IntInsnNode iin = (IntInsnNode) ain;
                if (iin.operand == 5) hasNewCharArray = true;
            }
            if (ain instanceof MethodInsnNode min) {
                if (min.owner.equals("java/lang/String") && (min.name.equals("<init>") || min.name.equals("valueOf"))) {
                    hasStringConstruct = true;
                }
                if (min.owner.equals("java/lang/String") && (min.name.equals("charAt") || min.name.equals("toCharArray"))) {
                    charOps++;
                }
            }
        }

        int score = xorOps + (charOps > 0 ? 1 : 0) + (arrayOps > 0 ? 1 : 0) + (hasNewCharArray ? 1 : 0) + (hasStringConstruct ? 1 : 0);
        return score >= 2;
    }
}
