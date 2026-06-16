package de.xbrowniecodez.jbytemod.diff;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class InsnSerializer {

    public static String serialize(AbstractInsnNode insn) {
        if (insn == null) return "NULL";

        int op = insn.getOpcode();
        String opName = op == -1 ? "PSEUDO" : opcodeToName(op);

        if (insn instanceof InsnNode) {
            return opName;
        }
        if (insn instanceof IntInsnNode) {
            return opName + " " + ((IntInsnNode) insn).operand;
        }
        if (insn instanceof VarInsnNode) {
            return opName + " " + ((VarInsnNode) insn).var;
        }
        if (insn instanceof TypeInsnNode) {
            return opName + " " + ((TypeInsnNode) insn).desc;
        }
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode fin = (FieldInsnNode) insn;
            return opName + " " + fin.owner + "." + fin.name + " " + fin.desc;
        }
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode min = (MethodInsnNode) insn;
            return opName + " " + min.owner + "." + min.name + min.desc;
        }
        if (insn instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) insn;
            return opName + " " + idin.name + idin.desc;
        }
        if (insn instanceof JumpInsnNode) {
            return opName + " L" + labelIndex((JumpInsnNode) insn);
        }
        if (insn instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) insn).cst;
            return opName + " " + cstToString(cst);
        }
        if (insn instanceof IincInsnNode) {
            IincInsnNode iin = (IincInsnNode) insn;
            return opName + " " + iin.var + " " + iin.incr;
        }
        if (insn instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode tsin = (TableSwitchInsnNode) insn;
            return opName + " " + tsin.min + "-" + tsin.max;
        }
        if (insn instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) insn;
            return opName + " keys=" + lsin.keys;
        }
        if (insn instanceof MultiANewArrayInsnNode) {
            MultiANewArrayInsnNode man = (MultiANewArrayInsnNode) insn;
            return opName + " " + man.desc + " " + man.dims;
        }
        if (insn instanceof LabelNode) {
            return "LABEL";
        }
        if (insn instanceof LineNumberNode) {
            return "LINE " + ((LineNumberNode) insn).line;
        }
        if (insn instanceof FrameNode) {
            return "FRAME";
        }
        return opName;
    }

    private static String cstToString(Object cst) {
        if (cst instanceof String) return "\"" + ((String) cst).replace("\"", "\\\"") + "\"";
        if (cst instanceof Long) return cst + "L";
        if (cst instanceof Float) return cst + "F";
        if (cst instanceof Double) return cst + "D";
        return String.valueOf(cst);
    }

    private static int labelIndex(JumpInsnNode jin) {
        LabelNode label = jin.label;
        AbstractInsnNode cur = jin;
        int idx = 0;
        while (cur != null) {
            if (cur == label) return idx;
            if (cur instanceof LabelNode) idx++;
            cur = cur.getPrevious();
        }
        cur = jin.getNext();
        idx = 0;
        while (cur != null) {
            if (cur == label) return idx;
            if (cur instanceof LabelNode) idx++;
            cur = cur.getNext();
        }
        return -1;
    }

    public static String opcodeToName(int opcode) {
        switch (opcode) {
            case Opcodes.NOP: return "NOP";
            case Opcodes.ACONST_NULL: return "ACONST_NULL";
            case Opcodes.ICONST_M1: return "ICONST_M1";
            case Opcodes.ICONST_0: return "ICONST_0";
            case Opcodes.ICONST_1: return "ICONST_1";
            case Opcodes.ICONST_2: return "ICONST_2";
            case Opcodes.ICONST_3: return "ICONST_3";
            case Opcodes.ICONST_4: return "ICONST_4";
            case Opcodes.ICONST_5: return "ICONST_5";
            case Opcodes.LCONST_0: return "LCONST_0";
            case Opcodes.LCONST_1: return "LCONST_1";
            case Opcodes.FCONST_0: return "FCONST_0";
            case Opcodes.FCONST_1: return "FCONST_1";
            case Opcodes.FCONST_2: return "FCONST_2";
            case Opcodes.DCONST_0: return "DCONST_0";
            case Opcodes.DCONST_1: return "DCONST_1";
            case Opcodes.BIPUSH: return "BIPUSH";
            case Opcodes.SIPUSH: return "SIPUSH";
            case Opcodes.LDC: return "LDC";
            case Opcodes.ILOAD: return "ILOAD";
            case Opcodes.LLOAD: return "LLOAD";
            case Opcodes.FLOAD: return "FLOAD";
            case Opcodes.DLOAD: return "DLOAD";
            case Opcodes.ALOAD: return "ALOAD";
            case Opcodes.IALOAD: return "IALOAD";
            case Opcodes.LALOAD: return "LALOAD";
            case Opcodes.FALOAD: return "FALOAD";
            case Opcodes.DALOAD: return "DALOAD";
            case Opcodes.AALOAD: return "AALOAD";
            case Opcodes.BALOAD: return "BALOAD";
            case Opcodes.CALOAD: return "CALOAD";
            case Opcodes.SALOAD: return "SALOAD";
            case Opcodes.ISTORE: return "ISTORE";
            case Opcodes.LSTORE: return "LSTORE";
            case Opcodes.FSTORE: return "FSTORE";
            case Opcodes.DSTORE: return "DSTORE";
            case Opcodes.ASTORE: return "ASTORE";
            case Opcodes.IASTORE: return "IASTORE";
            case Opcodes.LASTORE: return "LASTORE";
            case Opcodes.FASTORE: return "FASTORE";
            case Opcodes.DASTORE: return "DASTORE";
            case Opcodes.AASTORE: return "AASTORE";
            case Opcodes.BASTORE: return "BASTORE";
            case Opcodes.CASTORE: return "CASTORE";
            case Opcodes.SASTORE: return "SASTORE";
            case Opcodes.POP: return "POP";
            case Opcodes.POP2: return "POP2";
            case Opcodes.DUP: return "DUP";
            case Opcodes.DUP_X1: return "DUP_X1";
            case Opcodes.DUP_X2: return "DUP_X2";
            case Opcodes.DUP2: return "DUP2";
            case Opcodes.DUP2_X1: return "DUP2_X1";
            case Opcodes.DUP2_X2: return "DUP2_X2";
            case Opcodes.SWAP: return "SWAP";
            case Opcodes.IADD: return "IADD";
            case Opcodes.LADD: return "LADD";
            case Opcodes.FADD: return "FADD";
            case Opcodes.DADD: return "DADD";
            case Opcodes.ISUB: return "ISUB";
            case Opcodes.LSUB: return "LSUB";
            case Opcodes.FSUB: return "FSUB";
            case Opcodes.DSUB: return "DSUB";
            case Opcodes.IMUL: return "IMUL";
            case Opcodes.LMUL: return "LMUL";
            case Opcodes.FMUL: return "FMUL";
            case Opcodes.DMUL: return "DMUL";
            case Opcodes.IDIV: return "IDIV";
            case Opcodes.LDIV: return "LDIV";
            case Opcodes.FDIV: return "FDIV";
            case Opcodes.DDIV: return "DDIV";
            case Opcodes.IREM: return "IREM";
            case Opcodes.LREM: return "LREM";
            case Opcodes.FREM: return "FREM";
            case Opcodes.DREM: return "DREM";
            case Opcodes.INEG: return "INEG";
            case Opcodes.LNEG: return "LNEG";
            case Opcodes.FNEG: return "FNEG";
            case Opcodes.DNEG: return "DNEG";
            case Opcodes.ISHL: return "ISHL";
            case Opcodes.LSHL: return "LSHL";
            case Opcodes.ISHR: return "ISHR";
            case Opcodes.LSHR: return "LSHR";
            case Opcodes.IUSHR: return "IUSHR";
            case Opcodes.LUSHR: return "LUSHR";
            case Opcodes.IAND: return "IAND";
            case Opcodes.LAND: return "LAND";
            case Opcodes.IOR: return "IOR";
            case Opcodes.LOR: return "LOR";
            case Opcodes.IXOR: return "IXOR";
            case Opcodes.LXOR: return "LXOR";
            case Opcodes.IINC: return "IINC";
            case Opcodes.I2L: return "I2L";
            case Opcodes.I2F: return "I2F";
            case Opcodes.I2D: return "I2D";
            case Opcodes.L2I: return "L2I";
            case Opcodes.L2F: return "L2F";
            case Opcodes.L2D: return "L2D";
            case Opcodes.F2I: return "F2I";
            case Opcodes.F2L: return "F2L";
            case Opcodes.F2D: return "F2D";
            case Opcodes.D2I: return "D2I";
            case Opcodes.D2L: return "D2L";
            case Opcodes.D2F: return "D2F";
            case Opcodes.I2B: return "I2B";
            case Opcodes.I2C: return "I2C";
            case Opcodes.I2S: return "I2S";
            case Opcodes.LCMP: return "LCMP";
            case Opcodes.FCMPL: return "FCMPL";
            case Opcodes.FCMPG: return "FCMPG";
            case Opcodes.DCMPL: return "DCMPL";
            case Opcodes.DCMPG: return "DCMPG";
            case Opcodes.IFEQ: return "IFEQ";
            case Opcodes.IFNE: return "IFNE";
            case Opcodes.IFLT: return "IFLT";
            case Opcodes.IFGE: return "IFGE";
            case Opcodes.IFGT: return "IFGT";
            case Opcodes.IFLE: return "IFLE";
            case Opcodes.IF_ICMPEQ: return "IF_ICMPEQ";
            case Opcodes.IF_ICMPNE: return "IF_ICMPNE";
            case Opcodes.IF_ICMPLT: return "IF_ICMPLT";
            case Opcodes.IF_ICMPGE: return "IF_ICMPGE";
            case Opcodes.IF_ICMPGT: return "IF_ICMPGT";
            case Opcodes.IF_ICMPLE: return "IF_ICMPLE";
            case Opcodes.IF_ACMPEQ: return "IF_ACMPEQ";
            case Opcodes.IF_ACMPNE: return "IF_ACMPNE";
            case Opcodes.GOTO: return "GOTO";
            case Opcodes.JSR: return "JSR";
            case Opcodes.RET: return "RET";
            case Opcodes.TABLESWITCH: return "TABLESWITCH";
            case Opcodes.LOOKUPSWITCH: return "LOOKUPSWITCH";
            case Opcodes.IRETURN: return "IRETURN";
            case Opcodes.LRETURN: return "LRETURN";
            case Opcodes.FRETURN: return "FRETURN";
            case Opcodes.DRETURN: return "DRETURN";
            case Opcodes.ARETURN: return "ARETURN";
            case Opcodes.RETURN: return "RETURN";
            case Opcodes.GETSTATIC: return "GETSTATIC";
            case Opcodes.PUTSTATIC: return "PUTSTATIC";
            case Opcodes.GETFIELD: return "GETFIELD";
            case Opcodes.PUTFIELD: return "PUTFIELD";
            case Opcodes.INVOKEVIRTUAL: return "INVOKEVIRTUAL";
            case Opcodes.INVOKESPECIAL: return "INVOKESPECIAL";
            case Opcodes.INVOKESTATIC: return "INVOKESTATIC";
            case Opcodes.INVOKEINTERFACE: return "INVOKEINTERFACE";
            case Opcodes.INVOKEDYNAMIC: return "INVOKEDYNAMIC";
            case Opcodes.NEW: return "NEW";
            case Opcodes.NEWARRAY: return "NEWARRAY";
            case Opcodes.ANEWARRAY: return "ANEWARRAY";
            case Opcodes.ARRAYLENGTH: return "ARRAYLENGTH";
            case Opcodes.ATHROW: return "ATHROW";
            case Opcodes.CHECKCAST: return "CHECKCAST";
            case Opcodes.INSTANCEOF: return "INSTANCEOF";
            case Opcodes.MONITORENTER: return "MONITORENTER";
            case Opcodes.MONITOREXIT: return "MONITOREXIT";
            case Opcodes.MULTIANEWARRAY: return "MULTIANEWARRAY";
            case Opcodes.IFNULL: return "IFNULL";
            case Opcodes.IFNONNULL: return "IFNONNULL";
            default: return "OP_" + opcode;
        }
    }
}
