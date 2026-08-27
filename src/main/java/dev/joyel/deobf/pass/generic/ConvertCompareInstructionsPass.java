package dev.joyel.deobf.pass.generic;

import dev.joyel.deobf.analysis.InsnListUtil;
import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.*;

import java.util.Map;

public final class ConvertCompareInstructionsPass extends DeobfPass {

    public ConvertCompareInstructionsPass() {
        super(PassCategory.GENERIC,
                "Fix abnormal compare instructions",
                "Replaces LCMP/FCMP/DCMP outside of jump context with Long.compare / Float.compare / Double.compare. " +
                "Used by some obfuscators to confuse decompilers.",
                PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        int count = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode ain : mn.instructions) {
                    AbstractInsnNode next = InsnListUtil.getRealNext(ain);
                    if (next != null && next.getType() != AbstractInsnNode.JUMP_INSN) {
                        MethodInsnNode replacement = null;
                        switch (ain.getOpcode()) {
                            case LCMP:
                                replacement = new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "compare", "(JJ)I", false);
                                break;
                            case FCMPL: case FCMPG:
                                replacement = new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "compare", "(FF)I", false);
                                break;
                            case DCMPL: case DCMPG:
                                replacement = new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "compare", "(DD)I", false);
                                break;
                        }
                        if (replacement != null) {
                            mn.instructions.set(ain, replacement);
                            count++;
                        }
                    }
                }
            }
        }
        log("Converted " + count + " abnormal compare instructions.");
        return count > 0;
    }
}
