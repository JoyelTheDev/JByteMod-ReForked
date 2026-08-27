package dev.joyel.deobf.pass.generic;

import dev.joyel.deobf.analysis.InsnListUtil;
import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.*;

import java.util.Map;

public final class RemoveTryCatchObfuscationPass extends DeobfPass {

    private Map<String, ClassNode> classes;

    public RemoveTryCatchObfuscationPass() {
        super(PassCategory.GENERIC,
                "Remove try-catch flow obfuscation",
                "Removes fake try-catch handlers whose handler block immediately re-throws. " +
                "Effective against ZKM 8-11 and similar obfuscators.",
                PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        this.classes = classes;
        long before = countTcbs(classes);
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                mn.tryCatchBlocks.removeIf(this::isFakeTcb);
                mn.tryCatchBlocks.removeIf(tcb -> isEmpty(mn, tcb));
                InsnListUtil.removeDeadCode(cn, mn);
            }
        }
        long removed = before - countTcbs(classes);
        log("Removed " + removed + " fake try-catch blocks (of " + before + " total).");
        return removed > 0;
    }

    private long countTcbs(Map<String, ClassNode> classes) {
        return classes.values().stream()
                .flatMap(c -> c.methods.stream())
                .mapToLong(m -> m.tryCatchBlocks.size())
                .sum();
    }

    private boolean isEmpty(MethodNode mn, TryCatchBlockNode tcb) {
        return tcb.start == tcb.end
                || mn.instructions.indexOf(tcb.start) >= mn.instructions.indexOf(tcb.end);
    }

    private boolean isFakeTcb(TryCatchBlockNode tcb) {
        AbstractInsnNode first = InsnListUtil.getRealNext(tcb.handler);
        if (first == null) return true;
        if (first.getOpcode() == ATHROW) return true;
        if (first.getType() == AbstractInsnNode.METHOD_INSN) {
            AbstractInsnNode afterCall = InsnListUtil.getRealNext(first);
            if (afterCall != null && afterCall.getOpcode() == ATHROW) {
                MethodInsnNode min = (MethodInsnNode) first;
                ClassNode owner = classes.get(min.owner);
                if (owner == null) return false;
                MethodNode getter = getMethod(owner, min.name, min.desc);
                if (getter == null || (getter.access & ACC_NATIVE) != 0) return false;
                AbstractInsnNode getterFirst = getter.instructions.getFirst();
                while (getterFirst != null && getterFirst.getOpcode() == -1) getterFirst = getterFirst.getNext();
                if (getterFirst instanceof VarInsnNode && getterFirst.getNext() != null
                        && getterFirst.getNext().getOpcode() == ARETURN) {
                    return ((VarInsnNode) getterFirst).var == 0;
                }
            }
        }
        return false;
    }
}
