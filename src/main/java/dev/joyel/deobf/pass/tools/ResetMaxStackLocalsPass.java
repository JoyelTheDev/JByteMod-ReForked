package dev.joyel.deobf.pass.tools;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public final class ResetMaxStackLocalsPass extends DeobfPass {

    public ResetMaxStackLocalsPass() {
        super(PassCategory.TOOLS,
                "Reset maxStack / maxLocals",
                "Sets maxStack and maxLocals to a sentinel value (1337) to force ClassWriter to " +
                "recompute them on save. Run this after any pass that alters instructions.",
                PassTag.RUNNABLE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        classes.values().forEach(cn -> cn.methods.forEach(mn -> mn.maxLocals = mn.maxStack = 1337));
        log("Reset maxStack/maxLocals for all methods.");
        return true;
    }
}
