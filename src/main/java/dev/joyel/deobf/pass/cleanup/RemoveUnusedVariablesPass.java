package dev.joyel.deobf.pass.cleanup;

import dev.joyel.deobf.analysis.InsnListUtil;
import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class RemoveUnusedVariablesPass extends DeobfPass {

    public RemoveUnusedVariablesPass() {
        super(PassCategory.CLEANUP,
                "Remove unused local variables",
                "Replaces stores to variables that are never loaded with POP/POP2 instructions.",
                PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        int removed = 0;
        int modifiedMethods = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                int count = processMethod(mn);
                if (count > 0) {
                    modifiedMethods++;
                    removed += count;
                }
            }
        }
        log("Removed " + removed + " dead store(s) across " + modifiedMethods + " method(s).");
        return removed > 0;
    }

    private int processMethod(MethodNode mn) {
        List<VarInsnNode> varInsns = StreamSupport.stream(mn.instructions.spliterator(), false)
                .filter(a -> a.getType() == AbstractInsnNode.VAR_INSN)
                .map(a -> (VarInsnNode) a)
                .collect(Collectors.toList());

        Set<Integer> loaded = new HashSet<>();
        if ((mn.access & ACC_STATIC) == 0) loaded.add(0);
        varInsns.stream().filter(InsnListUtil::isLoadVar).map(v -> v.var).forEach(loaded::add);

        int removed = 0;
        for (VarInsnNode vin : varInsns) {
            if (InsnListUtil.isStoreVar(vin) && !loaded.contains(vin.var)) {
                if (InsnListUtil.isWideVar(vin)) {
                    mn.instructions.set(vin, new InsnNode(POP2));
                } else {
                    mn.instructions.set(vin, new InsnNode(POP));
                }
                removed++;
            }
        }
        return removed;
    }
}
