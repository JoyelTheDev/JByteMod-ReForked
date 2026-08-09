package dev.joyel.assembler;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

import java.util.*;

public final class OpcodeClasses {
    private static final Map<String, Class> NAME_TO_CLASS = new HashMap<String, Class>();
    private static final Map<Integer, Class> OPCODE_TO_CLASS = new HashMap<Integer, Class>();

    static {
        register(InsnNode.class, "nop","aconst_null","iconst_m1","iconst_0","iconst_1","iconst_2","iconst_3","iconst_4","iconst_5",
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
                "arraylength","athrow","monitorenter","monitorexit");
        register(MethodInsnNode.class, "invokestatic","invokevirtual","invokespecial","invokeinterface");
        register(FieldInsnNode.class, "getstatic","putstatic","getfield","putfield");
        register(VarInsnNode.class, "iload","lload","fload","dload","aload","istore","lstore","fstore","dstore","astore","ret");
        register(TypeInsnNode.class, "new","anewarray","checkcast","instanceof");
        register(MultiANewArrayInsnNode.class, "multianewarray");
        register(LdcInsnNode.class, "ldc");
        register(IincInsnNode.class, "iinc");
        register(JumpInsnNode.class, "ifeq","ifne","iflt","ifge","ifgt","ifle","if_icmpeq","if_icmpne","if_icmplt","if_icmpge","if_icmpgt","if_icmple","if_acmpeq","if_acmpne","goto","jsr","ifnull","ifnonnull");
        register(IntInsnNode.class, "bipush","sipush","newarray");
        register(InvokeDynamicInsnNode.class, "invokedynamic");
        register(TableSwitchInsnNode.class, "tableswitch");
        register(LookupSwitchInsnNode.class, "lookupswitch");
        register(LabelNode.class, "label");
        register(LineNumberNode.class, "line");
        register(FrameNode.class, "frame");
    }

    private static void register(Class type, String... names) {
        for (String name : names) {
            NAME_TO_CLASS.put(name, type);
            int idx = getOpcodeIndex(name);
            if (idx >= 0) OPCODE_TO_CLASS.put(idx, type);
        }
    }

    private OpcodeClasses() {}

    public static int getOpcodeIndex(String name) {
        for (int i = 0; i < Printer.OPCODES.length; i++) {
            if (Printer.OPCODES[i].equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    public static Class getOpcodeClass(String name) {
        return name == null ? null : NAME_TO_CLASS.get(name.toLowerCase(Locale.ROOT));
    }

    public static Map<String, Class> getNamesToClasses() {
        return Collections.unmodifiableMap(NAME_TO_CLASS);
    }

    public static AbstractInsnNode createDefault(String opcodeName, String owner, LabelNode target) {
        int opcode = getOpcodeIndex(opcodeName);
        Class type = getOpcodeClass(opcodeName);
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
        if (type == TableSwitchInsnNode.class) return new TableSwitchInsnNode(0, 0, label, new LabelNode[]{label});
        if (type == LookupSwitchInsnNode.class) return new LookupSwitchInsnNode(label, new int[]{0}, new LabelNode[]{label});
        if (type == MultiANewArrayInsnNode.class) return new MultiANewArrayInsnNode("[[Ljava/lang/Object;", 1);
        if (type == FrameNode.class) return new FrameNode(Opcodes.F_SAME, 0, null, 0, null);
        if (type == LineNumberNode.class) return new LineNumberNode(1, label);
        throw new IllegalArgumentException("Unsupported opcode: " + opcodeName);
    }
}
