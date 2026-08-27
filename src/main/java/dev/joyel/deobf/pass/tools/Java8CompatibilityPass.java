package dev.joyel.deobf.pass.tools;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public final class Java8CompatibilityPass extends DeobfPass {

    public Java8CompatibilityPass() {
        super(PassCategory.TOOLS,
                "Force Java 8 compatibility",
                "Sets the class file version of every class to 52 (Java 8). " +
                "Only works correctly when no Java 9+ APIs are used.",
                PassTag.POSSIBLE_DAMAGE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        classes.values().forEach(cn -> cn.version = 52);
        log("Set all class versions to 52 (Java 8).");
        return true;
    }
}
