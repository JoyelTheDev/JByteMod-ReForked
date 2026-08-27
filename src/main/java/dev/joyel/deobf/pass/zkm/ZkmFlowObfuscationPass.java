package dev.joyel.deobf.pass.zkm;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.function.Predicate;

public final class ZkmFlowObfuscationPass extends DeobfPass {

    private static final Predicate<Integer> SINGLE_JUMP =
            op -> (op >= IFEQ && op <= IFLE) || op == IFNULL || op == IFNONNULL;

    public ZkmFlowObfuscationPass() {
        super(PassCategory.ZKM,
                "ZKM flow obfuscation removal",
                "Removes ZKM-style garbage jumps: ILOAD/ALOAD immediately before a conditional jump " +
                "with no real purpose. Tested on ZKM 14.",
                PassTag.BETTER_DECOMPILE, PassTag.POSSIBLE_DAMAGE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        int removed = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                removed += processMethod(mn);
            }
        }
        log("Removed " + removed + " ZKM-style garbage jump(s).");
        return removed > 0;
    }

    private int processMethod(MethodNode mn) {
        int count = 0;
        for (AbstractInsnNode ain : mn.instructions.toArray()) {
            if (ain.getPrevious() == null) continue;
            if (!SINGLE_JUMP.test(ain.getOpcode())) continue;
            AbstractInsnNode prev = ain.getPrevious();
            boolean shouldRemove = false;
            if ((ain.getOpcode() == IFNULL || ain.getOpcode() == IFNONNULL) && prev.getOpcode() == ALOAD) {
                shouldRemove = true;
            } else if (ain.getOpcode() >= IFEQ && ain.getOpcode() <= IFLE && prev.getOpcode() == ILOAD) {
                shouldRemove = true;
            }
            if (shouldRemove) {
                mn.instructions.set(ain, new InsnNode(POP));
                count++;
            }
        }
        return count;
    }
}
