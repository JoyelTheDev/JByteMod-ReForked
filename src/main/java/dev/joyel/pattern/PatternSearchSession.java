package dev.joyel.pattern;

import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collections;
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
        this.methods = buildMethodList(jarArchive);
    }

    private List<MethodEntry> buildMethodList(JarArchive jarArchive) {
        List<MethodEntry> allMethods = new ArrayList<>();
        if (jarArchive != null && jarArchive.getClasses() != null) {
            for (Map.Entry<String, ClassNode> entry : jarArchive.getClasses().entrySet()) {
                ClassNode classNode = entry.getValue();
                if (classNode.methods != null) {
                    for (MethodNode methodNode : classNode.methods) {
                        if (methodNode.instructions != null && methodNode.instructions.size() > 0) {
                            allMethods.add(new MethodEntry(classNode, methodNode));
                        }
                    }
                }
            }
        }
        return allMethods;
    }

    public void advance(long budgetNanos) {
        if (isFinished()) {
            return;
        }

        long deadline = System.nanoTime() + Math.max(100_000L, budgetNanos);

        while (methodIndex < methods.size() && System.nanoTime() < deadline && !cancelled) {
            MethodEntry entry = methods.get(methodIndex++);
            List<InstructionPatternMatch> found = InstructionPatternMatcher.findAll(
                    entry.classNode,
                    entry.methodNode,
                    pattern
            );
            matchCount += found.size();

            int remaining = retainedResultLimit - results.size();
            if (remaining > 0) {
                int toAdd = Math.min(remaining, found.size());
                results.addAll(found.subList(0, toAdd));
            }
        }
    }

    public void cancel() {
        cancelled = true;
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
        return Collections.unmodifiableList(results);
    }

    public InstructionPattern pattern() {
        return pattern;
    }

    private static final class MethodEntry {
        final ClassNode classNode;
        final MethodNode methodNode;

        MethodEntry(ClassNode classNode, MethodNode methodNode) {
            this.classNode = classNode;
            this.methodNode = methodNode;
        }
    }
}