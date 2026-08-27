package dev.joyel.deobf.analysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.HashMap;
import java.util.Map;

public final class InsnListUtil implements Opcodes {

    private InsnListUtil() {}

    public static Map<LabelNode, LabelNode> cloneLabels(InsnList insns) {
        Map<LabelNode, LabelNode> map = new HashMap<>();
        for (AbstractInsnNode ain = insns.getFirst(); ain != null; ain = ain.getNext()) {
            if (ain.getType() == AbstractInsnNode.LABEL) {
                map.put((LabelNode) ain, new LabelNode());
            }
        }
        return map;
    }

    public static InsnList copy(InsnList insns) {
        InsnList copy = new InsnList();
        Map<LabelNode, LabelNode> labels = cloneLabels(insns);
        for (AbstractInsnNode ain : insns) {
            copy.add(ain.clone(labels));
        }
        return copy;
    }

    public static void updateInstructions(MethodNode m, Map<LabelNode, LabelNode> labels, InsnList rewritten) {
        m.instructions = rewritten;
        for (TryCatchBlockNode tcb : m.tryCatchBlocks) {
            tcb.start   = labels.getOrDefault(tcb.start, tcb.start);
            tcb.end     = labels.getOrDefault(tcb.end, tcb.end);
            tcb.handler = labels.getOrDefault(tcb.handler, tcb.handler);
        }
        if (m.localVariables != null) {
            for (LocalVariableNode lv : m.localVariables) {
                lv.start = labels.getOrDefault(lv.start, lv.start);
                lv.end   = labels.getOrDefault(lv.end, lv.end);
            }
        }
    }

    public static AbstractInsnNode getRealNext(AbstractInsnNode ain) {
        AbstractInsnNode cur = ain.getNext();
        while (cur != null) {
            int type = cur.getType();
            if (type != AbstractInsnNode.LABEL
                    && type != AbstractInsnNode.FRAME
                    && type != AbstractInsnNode.LINE) {
                return cur;
            }
            cur = cur.getNext();
        }
        return null;
    }

    public static boolean isReturn(int opcode) {
        return opcode >= IRETURN && opcode <= RETURN;
    }

    public static boolean isWideVar(VarInsnNode vin) {
        int op = vin.getOpcode();
        return op == LSTORE || op == LLOAD || op == DSTORE || op == DLOAD;
    }

    public static boolean isStoreVar(VarInsnNode vin) {
        return vin.getOpcode() >= ISTORE;
    }

    public static boolean isLoadVar(VarInsnNode vin) {
        return vin.getOpcode() < ISTORE;
    }

    public static boolean removeDeadCode(ClassNode cn, MethodNode mn) {
        Analyzer<?> a = new Analyzer<>(new BasicInterpreter());
        try {
            a.analyze(cn.name, mn);
        } catch (AnalyzerException e) {
            return false;
        }
        Frame<?>[] frames = a.getFrames();
        AbstractInsnNode[] insns = mn.instructions.toArray();
        for (int i = 0; i < frames.length; i++) {
            if (frames[i] == null && insns[i] != null
                    && insns[i].getType() != AbstractInsnNode.LABEL) {
                mn.instructions.remove(insns[i]);
                insns[i] = null;
            }
        }
        return true;
    }
}
