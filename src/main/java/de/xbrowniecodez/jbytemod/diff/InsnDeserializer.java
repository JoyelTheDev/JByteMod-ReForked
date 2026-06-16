package de.xbrowniecodez.jbytemod.diff;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class InsnDeserializer {

    public static AbstractInsnNode deserialize(String serialized) {
        if (serialized == null || serialized.equals("NULL")) return null;

        String[] parts = serialized.split(" ", 2);
        String opName = parts[0];
        String args = parts.length > 1 ? parts[1] : "";

        int opcode = nameToOpcode(opName);

        if (opName.equals("LABEL")) return new LabelNode();
        if (opName.equals("FRAME")) return new FrameNode(0, 0, null, 0, null);

        if (opcode == -1) return null;

        if (isSimpleInsn(opcode)) return new InsnNode(opcode);

        if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH || opcode == Opcodes.NEWARRAY) {
            try { return new IntInsnNode(opcode, Integer.parseInt(args.trim())); } catch (NumberFormatException e) { return null; }
        }

        if (isVarInsn(opcode)) {
            try { return new VarInsnNode(opcode, Integer.parseInt(args.trim())); } catch (NumberFormatException e) { return null; }
        }

        if (isTypeInsn(opcode)) {
            return new TypeInsnNode(opcode, args.trim());
        }

        if (isFieldInsn(opcode)) {
            String[] fp = args.trim().split(" ");
            if (fp.length >= 2) {
                String[] ownerName = fp[0].split("\\.");
                String owner = ownerName.length > 0 ? ownerName[0] : "";
                String name = ownerName.length > 1 ? ownerName[1] : "";
                return new FieldInsnNode(opcode, owner, name, fp[1]);
            }
            return null;
        }

        if (isMethodInsn(opcode)) {
            String trimmed = args.trim();
            int dotIdx = trimmed.indexOf('.');
            int parenIdx = trimmed.indexOf('(');
            if (dotIdx == -1 || parenIdx == -1) return null;
            String owner = trimmed.substring(0, dotIdx);
            String name = trimmed.substring(dotIdx + 1, parenIdx);
            String desc = trimmed.substring(parenIdx);
            boolean itf = opcode == Opcodes.INVOKEINTERFACE;
            return new MethodInsnNode(opcode, owner, name, desc, itf);
        }

        if (opcode == Opcodes.LDC) {
            return parseLdc(args.trim());
        }

        if (opcode == Opcodes.IINC) {
            String[] sp = args.trim().split(" ");
            if (sp.length >= 2) {
                try {
                    return new IincInsnNode(Integer.parseInt(sp[0]), Integer.parseInt(sp[1]));
                } catch (NumberFormatException e) { return null; }
            }
            return null;
        }

        if (opcode == Opcodes.GOTO || isJumpInsn(opcode)) {
            return new JumpInsnNode(opcode, new LabelNode());
        }

        if (opcode == Opcodes.MULTIANEWARRAY) {
            String[] sp = args.trim().split(" ");
            if (sp.length >= 2) {
                try {
                    return new MultiANewArrayInsnNode(sp[0], Integer.parseInt(sp[1]));
                } catch (NumberFormatException e) { return null; }
            }
            return null;
        }

        return new InsnNode(opcode);
    }

    private static AbstractInsnNode parseLdc(String args) {
        if (args.startsWith("\"")) {
            String s = args.substring(1);
            if (s.endsWith("\"")) s = s.substring(0, s.length() - 1);
            return new LdcInsnNode(s.replace("\\\"", "\""));
        }
        if (args.endsWith("L")) {
            try { return new LdcInsnNode(Long.parseLong(args.substring(0, args.length() - 1))); } catch (NumberFormatException ignored) {}
        }
        if (args.endsWith("F")) {
            try { return new LdcInsnNode(Float.parseFloat(args.substring(0, args.length() - 1))); } catch (NumberFormatException ignored) {}
        }
        if (args.endsWith("D")) {
            try { return new LdcInsnNode(Double.parseDouble(args.substring(0, args.length() - 1))); } catch (NumberFormatException ignored) {}
        }
        try { return new LdcInsnNode(Integer.parseInt(args)); } catch (NumberFormatException ignored) {}
        try { return new LdcInsnNode(Double.parseDouble(args)); } catch (NumberFormatException ignored) {}
        return new LdcInsnNode(args);
    }

    private static boolean isSimpleInsn(int op) {
        return (op >= Opcodes.NOP && op <= Opcodes.DCONST_1)
                || (op >= Opcodes.IALOAD && op <= Opcodes.SALOAD)
                || (op >= Opcodes.IASTORE && op <= Opcodes.SASTORE)
                || (op >= Opcodes.POP && op <= Opcodes.SWAP)
                || (op >= Opcodes.IADD && op <= Opcodes.DCMPG)
                || (op >= Opcodes.IRETURN && op <= Opcodes.RETURN)
                || op == Opcodes.ARRAYLENGTH || op == Opcodes.ATHROW
                || op == Opcodes.MONITORENTER || op == Opcodes.MONITOREXIT;
    }

    private static boolean isVarInsn(int op) {
        return (op >= Opcodes.ILOAD && op <= Opcodes.ALOAD)
                || (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE)
                || op == Opcodes.RET;
    }

    private static boolean isTypeInsn(int op) {
        return op == Opcodes.NEW || op == Opcodes.ANEWARRAY
                || op == Opcodes.CHECKCAST || op == Opcodes.INSTANCEOF;
    }

    private static boolean isFieldInsn(int op) {
        return op == Opcodes.GETSTATIC || op == Opcodes.PUTSTATIC
                || op == Opcodes.GETFIELD || op == Opcodes.PUTFIELD;
    }

    private static boolean isMethodInsn(int op) {
        return op == Opcodes.INVOKEVIRTUAL || op == Opcodes.INVOKESPECIAL
                || op == Opcodes.INVOKESTATIC || op == Opcodes.INVOKEINTERFACE;
    }

    private static boolean isJumpInsn(int op) {
        return (op >= Opcodes.IFEQ && op <= Opcodes.JSR)
                || op == Opcodes.IFNULL || op == Opcodes.IFNONNULL;
    }

    private static int nameToOpcode(String name) {
        switch (name) {
            case "NOP": return Opcodes.NOP;
            case "ACONST_NULL": return Opcodes.ACONST_NULL;
            case "ICONST_M1": return Opcodes.ICONST_M1;
            case "ICONST_0": return Opcodes.ICONST_0;
            case "ICONST_1": return Opcodes.ICONST_1;
            case "ICONST_2": return Opcodes.ICONST_2;
            case "ICONST_3": return Opcodes.ICONST_3;
            case "ICONST_4": return Opcodes.ICONST_4;
            case "ICONST_5": return Opcodes.ICONST_5;
            case "LCONST_0": return Opcodes.LCONST_0;
            case "LCONST_1": return Opcodes.LCONST_1;
            case "FCONST_0": return Opcodes.FCONST_0;
            case "FCONST_1": return Opcodes.FCONST_1;
            case "FCONST_2": return Opcodes.FCONST_2;
            case "DCONST_0": return Opcodes.DCONST_0;
            case "DCONST_1": return Opcodes.DCONST_1;
            case "BIPUSH": return Opcodes.BIPUSH;
            case "SIPUSH": return Opcodes.SIPUSH;
            case "LDC": return Opcodes.LDC;
            case "ILOAD": return Opcodes.ILOAD;
            case "LLOAD": return Opcodes.LLOAD;
            case "FLOAD": return Opcodes.FLOAD;
            case "DLOAD": return Opcodes.DLOAD;
            case "ALOAD": return Opcodes.ALOAD;
            case "IALOAD": return Opcodes.IALOAD;
            case "LALOAD": return Opcodes.LALOAD;
            case "FALOAD": return Opcodes.FALOAD;
            case "DALOAD": return Opcodes.DALOAD;
            case "AALOAD": return Opcodes.AALOAD;
            case "BALOAD": return Opcodes.BALOAD;
            case "CALOAD": return Opcodes.CALOAD;
            case "SALOAD": return Opcodes.SALOAD;
            case "ISTORE": return Opcodes.ISTORE;
            case "LSTORE": return Opcodes.LSTORE;
            case "FSTORE": return Opcodes.FSTORE;
            case "DSTORE": return Opcodes.DSTORE;
            case "ASTORE": return Opcodes.ASTORE;
            case "IASTORE": return Opcodes.IASTORE;
            case "LASTORE": return Opcodes.LASTORE;
            case "FASTORE": return Opcodes.FASTORE;
            case "DASTORE": return Opcodes.DASTORE;
            case "AASTORE": return Opcodes.AASTORE;
            case "BASTORE": return Opcodes.BASTORE;
            case "CASTORE": return Opcodes.CASTORE;
            case "SASTORE": return Opcodes.SASTORE;
            case "POP": return Opcodes.POP;
            case "POP2": return Opcodes.POP2;
            case "DUP": return Opcodes.DUP;
            case "DUP_X1": return Opcodes.DUP_X1;
            case "DUP_X2": return Opcodes.DUP_X2;
            case "DUP2": return Opcodes.DUP2;
            case "DUP2_X1": return Opcodes.DUP2_X1;
            case "DUP2_X2": return Opcodes.DUP2_X2;
            case "SWAP": return Opcodes.SWAP;
            case "IADD": return Opcodes.IADD;
            case "LADD": return Opcodes.LADD;
            case "FADD": return Opcodes.FADD;
            case "DADD": return Opcodes.DADD;
            case "ISUB": return Opcodes.ISUB;
            case "LSUB": return Opcodes.LSUB;
            case "FSUB": return Opcodes.FSUB;
            case "DSUB": return Opcodes.DSUB;
            case "IMUL": return Opcodes.IMUL;
            case "LMUL": return Opcodes.LMUL;
            case "FMUL": return Opcodes.FMUL;
            case "DMUL": return Opcodes.DMUL;
            case "IDIV": return Opcodes.IDIV;
            case "LDIV": return Opcodes.LDIV;
            case "FDIV": return Opcodes.FDIV;
            case "DDIV": return Opcodes.DDIV;
            case "IREM": return Opcodes.IREM;
            case "LREM": return Opcodes.LREM;
            case "FREM": return Opcodes.FREM;
            case "DREM": return Opcodes.DREM;
            case "INEG": return Opcodes.INEG;
            case "LNEG": return Opcodes.LNEG;
            case "FNEG": return Opcodes.FNEG;
            case "DNEG": return Opcodes.DNEG;
            case "ISHL": return Opcodes.ISHL;
            case "LSHL": return Opcodes.LSHL;
            case "ISHR": return Opcodes.ISHR;
            case "LSHR": return Opcodes.LSHR;
            case "IUSHR": return Opcodes.IUSHR;
            case "LUSHR": return Opcodes.LUSHR;
            case "IAND": return Opcodes.IAND;
            case "LAND": return Opcodes.LAND;
            case "IOR": return Opcodes.IOR;
            case "LOR": return Opcodes.LOR;
            case "IXOR": return Opcodes.IXOR;
            case "LXOR": return Opcodes.LXOR;
            case "IINC": return Opcodes.IINC;
            case "I2L": return Opcodes.I2L;
            case "I2F": return Opcodes.I2F;
            case "I2D": return Opcodes.I2D;
            case "L2I": return Opcodes.L2I;
            case "L2F": return Opcodes.L2F;
            case "L2D": return Opcodes.L2D;
            case "F2I": return Opcodes.F2I;
            case "F2L": return Opcodes.F2L;
            case "F2D": return Opcodes.F2D;
            case "D2I": return Opcodes.D2I;
            case "D2L": return Opcodes.D2L;
            case "D2F": return Opcodes.D2F;
            case "I2B": return Opcodes.I2B;
            case "I2C": return Opcodes.I2C;
            case "I2S": return Opcodes.I2S;
            case "LCMP": return Opcodes.LCMP;
            case "FCMPL": return Opcodes.FCMPL;
            case "FCMPG": return Opcodes.FCMPG;
            case "DCMPL": return Opcodes.DCMPL;
            case "DCMPG": return Opcodes.DCMPG;
            case "IFEQ": return Opcodes.IFEQ;
            case "IFNE": return Opcodes.IFNE;
            case "IFLT": return Opcodes.IFLT;
            case "IFGE": return Opcodes.IFGE;
            case "IFGT": return Opcodes.IFGT;
            case "IFLE": return Opcodes.IFLE;
            case "IF_ICMPEQ": return Opcodes.IF_ICMPEQ;
            case "IF_ICMPNE": return Opcodes.IF_ICMPNE;
            case "IF_ICMPLT": return Opcodes.IF_ICMPLT;
            case "IF_ICMPGE": return Opcodes.IF_ICMPGE;
            case "IF_ICMPGT": return Opcodes.IF_ICMPGT;
            case "IF_ICMPLE": return Opcodes.IF_ICMPLE;
            case "IF_ACMPEQ": return Opcodes.IF_ACMPEQ;
            case "IF_ACMPNE": return Opcodes.IF_ACMPNE;
            case "GOTO": return Opcodes.GOTO;
            case "JSR": return Opcodes.JSR;
            case "RET": return Opcodes.RET;
            case "TABLESWITCH": return Opcodes.TABLESWITCH;
            case "LOOKUPSWITCH": return Opcodes.LOOKUPSWITCH;
            case "IRETURN": return Opcodes.IRETURN;
            case "LRETURN": return Opcodes.LRETURN;
            case "FRETURN": return Opcodes.FRETURN;
            case "DRETURN": return Opcodes.DRETURN;
            case "ARETURN": return Opcodes.ARETURN;
            case "RETURN": return Opcodes.RETURN;
            case "GETSTATIC": return Opcodes.GETSTATIC;
            case "PUTSTATIC": return Opcodes.PUTSTATIC;
            case "GETFIELD": return Opcodes.GETFIELD;
            case "PUTFIELD": return Opcodes.PUTFIELD;
            case "INVOKEVIRTUAL": return Opcodes.INVOKEVIRTUAL;
            case "INVOKESPECIAL": return Opcodes.INVOKESPECIAL;
            case "INVOKESTATIC": return Opcodes.INVOKESTATIC;
            case "INVOKEINTERFACE": return Opcodes.INVOKEINTERFACE;
            case "INVOKEDYNAMIC": return Opcodes.INVOKEDYNAMIC;
            case "NEW": return Opcodes.NEW;
            case "NEWARRAY": return Opcodes.NEWARRAY;
            case "ANEWARRAY": return Opcodes.ANEWARRAY;
            case "ARRAYLENGTH": return Opcodes.ARRAYLENGTH;
            case "ATHROW": return Opcodes.ATHROW;
            case "CHECKCAST": return Opcodes.CHECKCAST;
            case "INSTANCEOF": return Opcodes.INSTANCEOF;
            case "MONITORENTER": return Opcodes.MONITORENTER;
            case "MONITOREXIT": return Opcodes.MONITOREXIT;
            case "MULTIANEWARRAY": return Opcodes.MULTIANEWARRAY;
            case "IFNULL": return Opcodes.IFNULL;
            case "IFNONNULL": return Opcodes.IFNONNULL;
            default: return -1;
        }
    }
}
