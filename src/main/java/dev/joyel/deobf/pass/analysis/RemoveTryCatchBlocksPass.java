package dev.joyel.deobf.pass.analysis;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public final class RemoveTryCatchBlocksPass extends DeobfPass {

    public RemoveTryCatchBlocksPass() {
        super(PassCategory.ANALYSIS,
                "Remove all try-catch blocks",
                "Clears every try-catch block in every method. " +
                "Very destructive — use only for deobfuscation analysis.",
                PassTag.POSSIBLE_DAMAGE, PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        long count = classes.values().stream()
                .flatMap(cn -> cn.methods.stream())
                .mapToLong(mn -> mn.tryCatchBlocks.size())
                .sum();
        classes.values().forEach(cn -> cn.methods.forEach(mn -> mn.tryCatchBlocks.clear()));
        log("Removed " + count + " try-catch block(s).");
        return count > 0;
    }
}
