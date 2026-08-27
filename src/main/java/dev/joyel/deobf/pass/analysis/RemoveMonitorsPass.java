package dev.joyel.deobf.pass.analysis;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.*;

import java.util.Map;

public final class RemoveMonitorsPass extends DeobfPass {

    public RemoveMonitorsPass() {
        super(PassCategory.ANALYSIS,
                "Remove synchronized blocks",
                "Replaces all MONITORENTER and MONITOREXIT instructions with POP. " +
                "May make code unrunnable in concurrent scenarios.",
                PassTag.POSSIBLE_DAMAGE, PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        int count = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode ain : mn.instructions.toArray()) {
                    if (ain.getOpcode() == MONITORENTER || ain.getOpcode() == MONITOREXIT) {
                        mn.instructions.set(ain, new InsnNode(POP));
                        count++;
                    }
                }
            }
        }
        log("Replaced " + count + " monitor instruction(s) with POP.");
        return count > 0;
    }
}
