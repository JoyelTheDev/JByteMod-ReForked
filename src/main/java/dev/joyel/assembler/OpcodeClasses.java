package dev.joyel.assembler;

import org.objectweb.asm.tree.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Printer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class OpcodeClasses {
    private static final Map<Class<? extends AbstractInsnNode>, String[]> CLASS_TO_NAMES = new HashMap<>();
    private static final Map<Integer, Class<? extends AbstractInsnNode>> OPCODE_TO_CLASS = new HashMap<>();
    private static final Map<String, Class<? extends AbstractInsnNode>> NAME_TO_CLASS = new HashMap<>();

    static {
        CLASS_TO_NAMES.put(InsnNode.class, new String[]{
                "nop","aconst_null","iconst_m1","iconst_0","iconst_1","iconst_2","iconst_3","iconst_4","iconst_5",
                "lconst_0","lconst_1","fconst_0","fconst_1","fconst_2","dconst_0","dconst_1",
                "iaload","laload","faload","daload","aaload","baload","caload","saload",
                "iastore","lastore","fastore","dastore","aastore","bastore","castore","sastore",
                "pop","pop2","dup","dup_x1","dup_x2","dup2","dup2_x1","dup2_x2","swap",
                "iadd","ladd","fadd","dadd","isub","lsub","fsub","dsub",
                "imul","lmul","fmul","dmul","idiv","ldiv","fdiv","ddiv",
                "irem","lrem","frem","drem","ineg","lneg","fneg","dneg",
                "ishl","lshl","ishr","lshr","iushr","lushr","iand","land","ior","lor","ixor","lxor",
                "i2l","i2f","i2d","l2i","l2f","l2d","f2i","f2l","f2d","d2i","d2l","d2f","i2b","i2c","i2s",
                "lcmp","fcmpl","fcmpg","dcmpl","dcmpg",
                "ireturn","lreturn","freturn","dreturn","areturn","return",
                "arraylength","athrow","monitorenter","monitorexit"
        });
        CLASS_TO_NAMES.put(MethodInsnNode.class, new String[]{"invokestatic","invokevirtual","invokespecial","invokeinterface"});
        CLASS_TO_NAMES.put(FieldInsnNode.class, new String[]{"getstatic","putstatic","getfield","putfield"});
        CLASS_TO_NAMES.put(VarInsnNode.class, new String[]{"iload","lload","fload","dload","aload","istore","lstore","fstore","dstore","astore","ret"});
        CLASS_TO_NAMES.put(TypeInsnNode.class, new String[]{"new","anewarray","checkcast","instanceof"});
        CLASS_TO_NAMES.put(MultiANewArrayInsnNode.class, new String[]{"multianewarray"});
        CLASS_TO_NAMES.put(LdcInsnNode.class, new String[]{"ldc"});
        CLASS_TO_NAMES.put(IincInsnNode.class, new String[]{"iinc"});
        CLASS_TO_NAMES.put(JumpInsnNode.class, new String[]{"ifeq","ifne","iflt","ifge","ifgt","ifle","if_icmpeq","if_icmpne","if_icmplt","if_icmpge","if_icmpgt","if_icmple","if_acmpeq","if_acmpne","goto","jsr","ifnull","ifnonnull"});
        CLASS_TO_NAMES.put(IntInsnNode.class, new String[]{"bipush","sipush","newarray"});
        CLASS_TO_NAMES.put(InvokeDynamicInsnNode.class, new String[]{"invokedynamic"});
        CLASS_TO_NAMES.put(TableSwitchInsnNode.class, new String[]{"tableswitch"});
        CLASS_TO_NAMES.put(LookupSwitchInsnNode.class, new String[]{"lookupswitch"});
        CLASS_TO_NAMES.put(LabelNode.class, new String[]{"label"});
        CLASS_TO_NAMES.put(LineNumberNode.class, new String[]{"line"});
        CLASS_TO_NAMES.put(FrameNode.class, new String[]{"frame"});

        for (Map.Entry<Class<? extends AbstractInsnNode>, String[]> entry : CLASS_TO_NAMES.entrySet()) {
            for (String name : entry.getValue()) {
                NAME_TO_CLASS.put(name, entry.getKey());
                int idx = getOpcodeIndex(name);
                OPCODE_TO_CLASS.put(idx, entry.getKey());
            }
        }
    }

    private OpcodeClasses() {
    }

    public static int getOpcodeIndex(String name) {
        for (int i = 0; i < Printer.OPCODES.length; i++) {
            if (Printer.OPCODES[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public static Class<? extends AbstractInsnNode> getOpcodeClass(String name) {
        return NAME_TO_CLASS.get(name == null ? null : name.toLowerCase());
    }

    public static Class<? extends AbstractInsnNode> getOpcodeClass(int opcode) {
        return Objects.requireNonNull(OPCODE_TO_CLASS.get(opcode));
    }

    public static Map<String, Class<? extends AbstractInsnNode>> getNamesToClasses() {
        return Collections.unmodifiableMap(NAME_TO_CLASS);
    }

    public static AbstractInsnNode createDefault(String opcodeName, String owner, LabelNode target) {
        int opcode = getOpcodeIndex(opcodeName);
        Class<?> type = getOpcodeClass(opcodeName);
        LabelNode label = target == null ? new LabelNode() : target;
        if (type == InsnNode.class) return new InsnNode(opcode);
        if (type == IntInsnNode.class) return new IntInsnNode(opcode, opcode == Opcodes.NEWARRAY ? Opcodes.T_INT : 0);
        if (type == VarInsnNode.class) return new VarInsnNode(opcode, 0);
        if (type == TypeInsnNode.class) return new TypeInsnNode(opcode, "java/lang/Object");
        if (type == FieldInsnNode.class) return new FieldInsnNode(opcode, owner, "field", "I");
        if (type == MethodInsnNode.class) return new MethodInsnNode(opcode, owner, "method", "()V", opcode == Opcodes.INVOKEINTERFACE);
        if (type == InvokeDynamicInsnNode.class) {
            Handle bsm = new Handle(Opcodes.H_INVOKESTATIC, owner, "bootstrap",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
            return new InvokeDynamicInsnNode("dynamic", "()V", bsm);
        }
        if (type == JumpInsnNode.class) return new JumpInsnNode(opcode, label);
        if (type == LabelNode.class) return new LabelNode();
        if (type == LdcInsnNode.class) return new LdcInsnNode("");
        if (type == IincInsnNode.class) return new IincInsnNode(0, 1);
        if (type == TableSwitchInsnNode.class) return new TableSwitchInsnNode(0, 0, label, label);
        if (type == LookupSwitchInsnNode.class) return new LookupSwitchInsnNode(label, new int[]{0}, new LabelNode[]{label});
        if (type == MultiANewArrayInsnNode.class) return new MultiANewArrayInsnNode("[[Ljava/lang/Object;", 1);
        if (type == FrameNode.class) return new FrameNode(Opcodes.F_SAME, 0, null, 0, null);
        if (type == LineNumberNode.class) return new LineNumberNode(1, label);
        throw new IllegalArgumentException("Unsupported opcode: " + opcodeName);
    }
}
