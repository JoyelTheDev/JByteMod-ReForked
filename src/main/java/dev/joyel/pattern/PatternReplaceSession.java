package dev.joyel.pattern;

import dev.joyel.assembler.AssemblerClipboardCodec;
import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class PatternReplaceSession {
    public enum State { RUNNING, FINISHED }

    public record ReplaceResult(ClassNode classNode, MethodNode method, int replacements) {
    }

    private final JarArchive jarArchive;
    private final InstructionPattern searchPattern;
    private final String replacementText;
    private final List<MethodEntry> methods;
    private final List<ReplaceResult> results = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private int methodIndex;
    private int totalReplacements;
    private State state = State.RUNNING;

    public PatternReplaceSession(JarArchive jarArchive, InstructionPattern searchPattern, String replacementText) {
        this.jarArchive = jarArchive;
        this.searchPattern = searchPattern;
        this.replacementText = replacementText;
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
        if (state != State.RUNNING) return;
        long deadline = System.nanoTime() + Math.max(100_000L, budgetNanos);
        do {
            MethodEntry entry = methods.get(methodIndex++);
            try {
                int replaced = replaceInMethod(entry.classNode(), entry.methodNode());
                if (replaced > 0) {
                    results.add(new ReplaceResult(entry.classNode(), entry.methodNode(), replaced));
                    totalReplacements += replaced;
                }
            } catch (Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                errors.add(entry.classNode().name + "#" + entry.methodNode().name + entry.methodNode().desc + ": " + msg);
            }
        } while (methodIndex < methods.size() && System.nanoTime() < deadline);

        if (methodIndex >= methods.size()) state = State.FINISHED;
    }

    private int replaceInMethod(ClassNode owner, MethodNode method) {
        List<InstructionPatternMatch> matches = InstructionPatternMatcher.findAll(owner, method, searchPattern);
        if (matches.isEmpty()) return 0;

        List<AbstractInsnNode> replacement = parseReplacement(method);

        Set<AbstractInsnNode> matchedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AbstractInsnNode> matchHeads = Collections.newSetFromMap(new IdentityHashMap<>());
        for (InstructionPatternMatch match : matches) {
            for (AbstractInsnNode insn : match.getInstructions()) matchedSet.add(insn);
            if (!match.getInstructions().isEmpty()) matchHeads.add(match.getInstructions().get(0));
        }

        InsnList newList = new InsnList();
        for (AbstractInsnNode insn : method.instructions) {
            if (!matchedSet.contains(insn)) {
                newList.add(insn.clone(new IdentityHashMap<>()));
            } else if (matchHeads.contains(insn)) {
                for (AbstractInsnNode rep : replacement) {
                    newList.add(rep.clone(new IdentityHashMap<>()));
                }
            }
        }
        method.instructions = newList;
        return matches.size();
    }

    private List<AbstractInsnNode> parseReplacement(MethodNode context) {
        Map<String, LabelNode> existingLabels = new LinkedHashMap<>();
        for (AbstractInsnNode insn : context.instructions) {
            if (insn instanceof LabelNode l) existingLabels.put("L" + existingLabels.size(), l);
        }
        AssemblerClipboardCodec.ParsedInstructions parsed = AssemblerClipboardCodec.parse(
                replacementText,
                name -> existingLabels.getOrDefault(name, new LabelNode()));
        return parsed.instructions();
    }

    public boolean isFinished() { return state == State.FINISHED; }
    public float progress() { return methods.isEmpty() ? 1f : (float) methodIndex / methods.size(); }
    public int methodsProcessed() { return methodIndex; }
    public int methodCount() { return methods.size(); }
    public int totalReplacements() { return totalReplacements; }
    public int methodsModified() { return results.size(); }
    public List<ReplaceResult> results() { return List.copyOf(results); }
    public List<String> errors() { return List.copyOf(errors); }

    private record MethodEntry(ClassNode classNode, MethodNode methodNode) {
    }
}
