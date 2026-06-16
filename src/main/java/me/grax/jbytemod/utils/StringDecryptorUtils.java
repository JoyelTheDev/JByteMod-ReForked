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
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.Opcodes.*;

public class StringDecryptorUtils {

    public static int decryptStrings(Map<String, ClassNode> classes, String targetOwner, String targetName, String targetDesc, byte[] jarBytes) {
        AtomicInteger count = new AtomicInteger();

        Class<?> decryptorClass = loadDecryptorClass(targetOwner, jarBytes);
        if (decryptorClass == null) {
            return -1;
        }

        Method decryptMethod = resolveMethod(decryptorClass, targetName, targetDesc);
        if (decryptMethod == null) {
            return -1;
        }

        String normalizedOwner = targetOwner.replace('.', '/');

        classes.values().forEach(classNode ->
            classNode.methods.forEach(methodNode -> {
                if (methodNode.instructions.getFirst() == null) {
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
                            Object decrypted = decryptMethod.invoke(null, argValues);
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

    private static Class<?> loadDecryptorClass(String owner, byte[] jarBytes) {
        try {
            java.io.File tempJar = java.io.File.createTempFile("jbm_decrypt_", ".jar");
            tempJar.deleteOnExit();
            java.nio.file.Files.write(tempJar.toPath(), jarBytes);
            URLClassLoader loader = new URLClassLoader(new URL[]{tempJar.toURI().toURL()}, ClassLoader.getSystemClassLoader());
            String className = owner.replace('/', '.');
            return Class.forName(className, true, loader);
        } catch (Throwable t) {
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
            if (ain instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) ain;
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
