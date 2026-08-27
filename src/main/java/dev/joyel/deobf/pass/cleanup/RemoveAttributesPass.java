package dev.joyel.deobf.pass.cleanup;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public final class RemoveAttributesPass extends DeobfPass {

    public RemoveAttributesPass() {
        super(PassCategory.CLEANUP,
                "Remove debug attributes",
                "Removes local variable name tables and generic signatures. " +
                "Reduces file size and removes obfuscated attribute noise.",
                PassTag.SHRINK, PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        for (ClassNode cn : classes.values()) {
            cn.signature = null;
            cn.methods.forEach(mn -> {
                mn.localVariables = null;
                mn.signature = null;
            });
            cn.fields.forEach(fn -> fn.signature = null);
        }
        log("Stripped all local variable and generic signature attributes.");
        return true;
    }
}
