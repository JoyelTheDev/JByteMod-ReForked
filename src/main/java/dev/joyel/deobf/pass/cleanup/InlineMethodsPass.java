package dev.joyel.deobf.pass.cleanup;

import dev.joyel.deobf.analysis.InsnListUtil;
import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.StreamSupport;

public final class InlineMethodsPass extends DeobfPass {

    public InlineMethodsPass() {
        super(PassCategory.CLEANUP,
                "Inline trivial static methods",
                "Inlines static methods with no branches, field/method calls, or jumps — " +
                "commonly injected by obfuscators to wrap simple returns.",
                PassTag.SHRINK, PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        Map<String, MethodNode> candidates = new HashMap<>();
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if (isTriviallySafe(mn)) {
                    candidates.put(cn.name + "." + mn.name + mn.desc, mn);
                }
            }
        }
        if (verbose) log(candidates.size() + " trivial methods found as inline candidates.");

        int inlines = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode ain : mn.instructions.toArray()) {
                    if (ain.getOpcode() != INVOKESTATIC) continue;
                    MethodInsnNode min = (MethodInsnNode) ain;
                    String key = min.owner + "." + min.name + min.desc;
                    MethodNode target = candidates.get(key);
                    if (target == null) continue;
                    inlineInto(mn, min, target);
                    mn.maxStack  = Math.max(mn.maxStack,  target.maxStack);
                    mn.maxLocals = Math.max(mn.maxLocals, target.maxLocals);
                    inlines++;
                }
            }
        }

        for (Map.Entry<String, MethodNode> e : candidates.entrySet()) {
            String owner = e.getKey().substring(0, e.getKey().lastIndexOf('.'));
            ClassNode cn = classes.get(owner);
            if (cn != null) cn.methods.remove(e.getValue());
        }

        log("Inlined " + inlines + " method call(s).");
        return inlines > 0;
    }

    private boolean isTriviallySafe(MethodNode mn) {
        if ((mn.access & ACC_STATIC) == 0) return false;
        if (mn.instructions.size() < 2 || mn.instructions.size() > 32) return false;
        return StreamSupport.stream(mn.instructions.spliterator(), false)
                .noneMatch(ain -> ain.getType() == AbstractInsnNode.METHOD_INSN
                        || ain.getType() == AbstractInsnNode.FIELD_INSN
                        || ain.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN
                        || ain.getType() == AbstractInsnNode.TYPE_INSN
                        || ain.getType() == AbstractInsnNode.JUMP_INSN);
    }

    private void inlineInto(MethodNode host, MethodInsnNode callSite, MethodNode target) {
        InsnList copy = InsnListUtil.copy(target.instructions);
        StreamSupport.stream(copy.spliterator(), false)
                .filter(a -> a.getType() == AbstractInsnNode.LINE || a.getType() == AbstractInsnNode.FRAME)
                .forEach(copy::remove);
        trimFinalReturn(copy);

        int localOffset = host.maxLocals + 4;
        for (AbstractInsnNode a : copy) {
            if (a.getType() == AbstractInsnNode.VAR_INSN) ((VarInsnNode) a).var += localOffset;
        }

        host.instructions.insertBefore(callSite, copy);
        host.instructions.remove(callSite);
    }

    private void trimFinalReturn(InsnList list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            AbstractInsnNode ain = list.get(i);
            if (ain.getOpcode() == ATHROW) return;
            list.remove(ain);
            int op = ain.getOpcode();
            if (op == RETURN || op == ARETURN || op == IRETURN
                    || op == FRETURN || op == DRETURN || op == LRETURN) return;
        }
    }
}
