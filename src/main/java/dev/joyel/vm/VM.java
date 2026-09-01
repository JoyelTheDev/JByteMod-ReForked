package dev.joyel.vm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

public class VM extends ClassLoader implements Opcodes {

    private static final Logger LOGGER = Logger.getLogger(VM.class.getName());
    public static final String RT_REGEX = "((?:com\\.(?:oracle|sun)|j(?:avax?|dk)|sun)\\.).*";

    private static final String JBYTEMOD_PKG = "de.xbrowniecodez.jbytemod";
    private static final String GRAX_PKG     = "me.grax.jbytemod";

    public final Map<String, Class<?>> loaded = new HashMap<>();

    private final IVMReferenceHandler handler;
    private final boolean noInitialization;
    private boolean dummyLoading;

    private VM(IVMReferenceHandler handler, ClassLoader parent, boolean noInitialization) {
        super(parent);
        this.handler = handler;
        this.noInitialization = noInitialization;
        this.dummyLoading = false;
    }

    public static VM constructVM(IVMReferenceHandler handler) {
        return new VM(handler, ClassLoader.getSystemClassLoader(), false);
    }

    public static VM constructNonInitializingVM(IVMReferenceHandler handler) {
        return new VM(handler, ClassLoader.getSystemClassLoader(), true);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.contains("/")) {
            throw new IllegalArgumentException("Class name must use dots, not slashes: " + name);
        }

        if (isForbiddenName(name)) {
            LOGGER.warning("VM: blocked access to protected package: " + name);
            return null;
        }

        if (loaded.containsKey(name)) {
            return loaded.get(name);
        }

        if (name.matches(RT_REGEX)) {
            return super.loadClass(name, resolve);
        }

        String internalName = name.replace('.', '/');
        ClassNode node = handler.tryClassLoad(internalName);
        byte[] bytes = toBytes(name, node, noInitialization, null);
        if (bytes == null) {
            return null;
        }

        Class<?> clazz = defineChecked(name, bytes);
        if (clazz != null) {
            loaded.put(name, clazz);
        }
        return clazz;
    }

    public void explicitlyPreload(ClassNode node) {
        explicitlyPreload(node, false);
    }

    public void explicitlyPreload(ClassNode node, boolean removeClinit) {
        explicitlyPreload(node, removeClinit, null);
    }

    public void explicitlyPreload(ClassNode node, boolean removeClinit, BiPredicate<String, String> callFilter) {
        String name = node.name.replace('/', '.');
        if (loaded.containsKey(name)) {
            return;
        }
        byte[] bytes = toBytes(name, node, removeClinit, callFilter);
        if (bytes == null) {
            return;
        }
        Class<?> clazz = defineChecked(name, bytes);
        if (clazz != null) {
            loaded.put(name, clazz);
        }
    }

    public boolean isLoaded(String dotName) {
        if (dotName.contains("/")) {
            throw new IllegalArgumentException("Use dot-separated names");
        }
        return loaded.containsKey(dotName);
    }

    public void setDummyLoading(boolean value) {
        this.dummyLoading = value;
    }

    private Class<?> defineChecked(String name, byte[] bytes) {
        if (loaded.containsKey(name)) {
            return loaded.get(name);
        }
        if (isForbiddenName(name)) {
            LOGGER.warning("VM: rejected define for protected name: " + name);
            return null;
        }
        try {
            Class<?> c = defineClass(name, bytes, 0, bytes.length, (ProtectionDomain) null);
            resolveClass(c);
            return c;
        } catch (Throwable t) {
            LOGGER.severe("VM: defineClass failed for " + name + ": " + t.getMessage());
            return null;
        }
    }

    private byte[] toBytes(String dotName, ClassNode node, boolean stripClinit, BiPredicate<String, String> callFilter) {
        if (node == null) {
            if (!dummyLoading) {
                return null;
            }
            ClassNode dummy = new ClassNode();
            dummy.name = dotName.replace('.', '/');
            dummy.superName = "java/lang/Object";
            dummy.version = 52;
            dummy.methods = new ArrayList<>();
            dummy.fields = new ArrayList<>();
            dummy.interfaces = new ArrayList<>();
            return writeNode(dummy);
        }

        ClassNode work = cloneNode(node);

        work.methods.forEach(m -> m.access = makePublic(m.access));
        work.fields.forEach(f -> f.access = makePublic(f.access));
        work.access = makePublic(work.access);

        if (stripClinit) {
            work.methods.stream()
                    .filter(m -> "<clinit>".equals(m.name))
                    .findFirst()
                    .ifPresent(m -> {
                        m.instructions.clear();
                        m.instructions.add(new InsnNode(RETURN));
                        m.tryCatchBlocks.clear();
                        m.localVariables = null;
                    });

            if ((work.access & ACC_ENUM) != 0) {
                work.superName = "java/lang/Enum";
            } else {
                work.superName = "java/lang/Object";
            }
            work.interfaces = new ArrayList<>();
        }

        if (callFilter != null) {
            work.methods.forEach(m -> stripFilteredCalls(m, callFilter));
        }

        return writeNode(work);
    }

    private static byte[] writeNode(ClassNode node) {
        try {
            ClassWriter cw = new ClassWriter(0);
            node.accept(cw);
            return cw.toByteArray();
        } catch (Throwable t) {
            LOGGER.severe("VM: ClassWriter failed: " + t.getMessage());
            return null;
        }
    }

    private static ClassNode cloneNode(ClassNode original) {
        ClassWriter cw = new ClassWriter(0);
        original.accept(cw);
        ClassReader cr = new ClassReader(cw.toByteArray());
        ClassNode clone = new ClassNode();
        try {
            cr.accept(clone, ClassReader.EXPAND_FRAMES);
        } catch (Throwable t) {
            cr.accept(clone, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
        }
        return clone;
    }

    private static int makePublic(int access) {
        access &= ~(ACC_PRIVATE | ACC_PROTECTED);
        access |= ACC_PUBLIC;
        return access;
    }

    private static boolean isForbiddenName(String dotName) {
        return dotName.startsWith(JBYTEMOD_PKG)
                || dotName.startsWith(GRAX_PKG)
                || dotName.matches(RT_REGEX);
    }

    private static void stripFilteredCalls(MethodNode mn, BiPredicate<String, String> filter) {
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode ain = mn.instructions.get(i);

            if (ain instanceof MethodInsnNode min) {
                if (filter.test(min.owner, min.desc)) {
                    org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(min.desc);
                    for (int j = args.length - 1; j >= 0; j--) {
                        mn.instructions.insertBefore(min, new InsnNode(args[j].getSize() > 1 ? POP2 : POP));
                        i++;
                    }
                    if (min.getOpcode() != INVOKESTATIC) {
                        mn.instructions.insertBefore(min, new InsnNode(POP));
                        i++;
                    }
                    mn.instructions.set(min, nullPush(org.objectweb.asm.Type.getReturnType(min.desc)));
                }

            } else if (ain instanceof FieldInsnNode fin) {
                if (filter.test(fin.owner, fin.desc)) {
                    org.objectweb.asm.Type type = org.objectweb.asm.Type.getType(fin.desc);
                    int op = fin.getOpcode();
                    if (op == GETFIELD) {
                        mn.instructions.insertBefore(fin, new InsnNode(POP));
                        i++;
                        mn.instructions.set(fin, nullPush(type));
                    } else if (op == GETSTATIC) {
                        mn.instructions.set(fin, nullPush(type));
                    } else if (op == PUTFIELD) {
                        mn.instructions.insertBefore(fin, new InsnNode(POP));
                        mn.instructions.insertBefore(fin, new InsnNode(type.getSize() > 1 ? POP2 : POP));
                        mn.instructions.set(fin, new InsnNode(NOP));
                        i += 2;
                    } else if (op == PUTSTATIC) {
                        mn.instructions.insertBefore(fin, new InsnNode(type.getSize() > 1 ? POP2 : POP));
                        mn.instructions.set(fin, new InsnNode(NOP));
                        i++;
                    }
                }
            }
        }

        if (mn.tryCatchBlocks != null) {
            mn.tryCatchBlocks.removeIf(tcb ->
                    filter.test(tcb.type == null ? "java/lang/Throwable" : tcb.type, ""));
        }
    }

    private static AbstractInsnNode nullPush(org.objectweb.asm.Type type) {
        switch (type.getSort()) {
            case org.objectweb.asm.Type.OBJECT:
            case org.objectweb.asm.Type.ARRAY:
                return new InsnNode(ACONST_NULL);
            case org.objectweb.asm.Type.VOID:
                return new InsnNode(NOP);
            case org.objectweb.asm.Type.DOUBLE:
                return new InsnNode(DCONST_0);
            case org.objectweb.asm.Type.FLOAT:
                return new InsnNode(FCONST_0);
            case org.objectweb.asm.Type.LONG:
                return new InsnNode(LCONST_0);
            default:
                return new InsnNode(ICONST_0);
        }
    }
}
