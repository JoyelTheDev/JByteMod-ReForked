package dev.joyel.pattern;

import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PatternSearchSession {
    private final InstructionPattern pattern;
    private final List<MethodEntry> methods;
    private final int retainedResultLimit;
    private final List<InstructionPatternMatch> results = new ArrayList<>();
    private int methodIndex;
    private long matchCount;
    private boolean cancelled;

    public PatternSearchSession(JarArchive jarArchive, InstructionPattern pattern, int retainedResultLimit) {
        this.pattern = pattern;
        this.retainedResultLimit = Math.max(1, retainedResultLimit);
        List<MethodEntry> all = new ArrayList<>();
        if (jarArchive != null && jarArchive.getClasses() != null) {
            for (Map.Entry<String, ClassNode> entry : jarArchive.getClasses().entrySet()) {
                ClassNode cn = entry.getValue();
                if (cn.methods != null) {
                    for (MethodNode mn : cn.methods) {
                        if (mn.instructions != null && mn.instructions.size() > 0) {
                            all.add(new MethodEntry(cn, mn));
                        }
                    }
                }
            }
        }
        this.methods = all;
    }

    public void advance(long budgetNanos) {
        if (isFinished()) return;
        long deadline = System.nanoTime() + Math.max(100_000L, budgetNanos);
        do {
            MethodEntry entry = methods.get(methodIndex++);
            List<InstructionPatternMatch> found = InstructionPatternMatcher.findAll(entry.classNode(), entry.methodNode(), pattern);
            matchCount += found.size();
            int remaining = retainedResultLimit - results.size();
            if (remaining > 0) results.addAll(found.subList(0, Math.min(remaining, found.size())));
        } while (methodIndex < methods.size() && System.nanoTime() < deadline && !cancelled);
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isFinished() {
        return cancelled || methodIndex >= methods.size();
    }

    public float progress() {
        return methods.isEmpty() ? 1f : (float) methodIndex / methods.size();
    }

    public int methodsSearched() {
        return methodIndex;
    }

    public int methodCount() {
        return methods.size();
    }

    public long matchCount() {
        return matchCount;
    }

    public List<InstructionPatternMatch> results() {
        return List.copyOf(results);
    }

    public InstructionPattern pattern() {
        return pattern;
    }

    private record MethodEntry(ClassNode classNode, MethodNode methodNode) {
    }
}
