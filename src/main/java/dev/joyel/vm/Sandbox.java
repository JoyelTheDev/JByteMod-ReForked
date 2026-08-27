package dev.joyel.vm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class Sandbox implements Opcodes {

    private Sandbox() {
    }

    public static MethodNode createMethodProxy(InsnList code, String name, String desc) {
        boolean isConstructor = name.equals("<init>");
        int access = isConstructor ? ACC_PUBLIC : ACC_PUBLIC | ACC_STATIC;
        MethodNode proxy = new MethodNode(access, name, desc, null, null);
        if (isConstructor) {
            proxy.instructions.add(buildSuperCall());
        }
        proxy.instructions.add(code);
        proxy.maxStack = 1337;
        proxy.maxLocals = 1337;
        return proxy;
    }

    private static InsnList buildSuperCall() {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(ALOAD, 0));
        list.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V"));
        return list;
    }

    public static ClassNode createClassProxy(String name) {
        ClassNode proxy = new ClassNode();
        proxy.access = ACC_PUBLIC;
        proxy.version = 52;
        proxy.name = name;
        proxy.superName = "java/lang/Object";
        proxy.interfaces = new ArrayList<>();
        proxy.fields = new ArrayList<>();
        proxy.methods = new ArrayList<>();
        return proxy;
    }

    public static MethodNode copyMethod(MethodNode original) {
        MethodNode copy = new MethodNode(
                original.access,
                original.name,
                original.desc,
                original.signature,
                original.exceptions == null ? null : original.exceptions.toArray(new String[0])
        );

        Map<LabelNode, LabelNode> labelMap = cloneLabels(original.instructions);

        InsnList copiedInsns = new InsnList();
        for (AbstractInsnNode ain : original.instructions) {
            copiedInsns.add(ain.clone(labelMap));
        }
        copy.instructions = copiedInsns;

        if (original.tryCatchBlocks != null) {
            copy.tryCatchBlocks = original.tryCatchBlocks.stream()
                    .map(tcb -> new TryCatchBlockNode(
                            labelMap.getOrDefault(tcb.start, tcb.start),
                            labelMap.getOrDefault(tcb.end, tcb.end),
                            labelMap.getOrDefault(tcb.handler, tcb.handler),
                            tcb.type))
                    .collect(Collectors.toList());
        }

        if (original.localVariables != null) {
            copy.localVariables = original.localVariables.stream()
                    .map(lv -> new LocalVariableNode(
                            lv.name, lv.desc, lv.signature,
                            labelMap.getOrDefault(lv.start, lv.start),
                            labelMap.getOrDefault(lv.end, lv.end),
                            lv.index))
                    .collect(Collectors.toList());
        }

        copy.maxStack = 1337;
        copy.maxLocals = 1337;
        return copy;
    }

    public static ClassNode fullClassProxy(ClassNode cn) {
        return fullClassProxy(cn, false);
    }

    public static ClassNode fullClassProxy(ClassNode cn, boolean keepSuperClass) {
        ClassNode clone = new ClassNode();
        clone.access = cn.access;
        clone.version = 52;
        clone.name = cn.name;
        clone.sourceFile = cn.sourceFile;
        clone.superName = (keepSuperClass && cn.superName != null && !cn.superName.equals("java/lang/Object"))
                ? cn.superName
                : "java/lang/Object";
        clone.interfaces = new ArrayList<>();
        clone.fields = new ArrayList<>();
        clone.methods = new ArrayList<>();

        if (cn.fields != null) {
            cn.fields.forEach(f -> clone.fields.add(new FieldNode(f.access, f.name, f.desc, f.signature, f.value)));
        }
        if (cn.methods != null) {
            cn.methods.forEach(m -> clone.methods.add(copyMethod(m)));
        }
        return clone;
    }

    private static Map<LabelNode, LabelNode> cloneLabels(InsnList insns) {
        Map<LabelNode, LabelNode> map = new HashMap<>();
        for (AbstractInsnNode ain = insns.getFirst(); ain != null; ain = ain.getNext()) {
            if (ain instanceof LabelNode) {
                map.put((LabelNode) ain, new LabelNode());
            }
        }
        return map;
    }

    public static List<ClassNode> wrapForExecution(ClassNode target, List<ClassNode> dependencies) {
        List<ClassNode> proxies = new ArrayList<>();
        proxies.add(fullClassProxy(target, true));
        if (dependencies != null) {
            for (ClassNode dep : dependencies) {
                proxies.add(fullClassProxy(dep, true));
            }
        }
        return proxies;
    }
}
