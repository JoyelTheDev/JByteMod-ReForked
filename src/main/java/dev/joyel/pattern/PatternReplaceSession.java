package dev.joyel.pattern;

import dev.joyel.assembler.AssemblerClipboardCodec;
import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class PatternReplaceSession {
    public enum State {
        RUNNING,
        FINISHED
    }

    public static final class ReplaceResult {
        private final ClassNode classNode;
        private final MethodNode method;
        private final int replacements;

        public ReplaceResult(ClassNode classNode, MethodNode method, int replacements) {
            this.classNode = classNode;
            this.method = method;
            this.replacements = replacements;
        }

        public ClassNode classNode() {
            return classNode;
        }

        public MethodNode method() {
            return method;
        }

        public int replacements() {
            return replacements;
        }
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
        if (state != State.RUNNING) {
            return;
        }

        long deadline = System.nanoTime() + Math.max(100_000L, budgetNanos);

        while (methodIndex < methods.size() && System.nanoTime() < deadline) {
            MethodEntry entry = methods.get(methodIndex++);
            try {
                int replaced = replaceInMethod(entry.classNode, entry.methodNode);
                if (replaced > 0) {
                    results.add(new ReplaceResult(entry.classNode, entry.methodNode, replaced));
                    totalReplacements += replaced;
                }
            } catch (Throwable t) {
                String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                errors.add(entry.classNode.name + "#" + entry.methodNode.name + entry.methodNode.desc + ": " + message);
            }
        }

        if (methodIndex >= methods.size()) {
            state = State.FINISHED;
        }
    }

    private int replaceInMethod(ClassNode owner, MethodNode method) {
        List<InstructionPatternMatch> matches = InstructionPatternMatcher.findAll(owner, method, searchPattern);
        if (matches.isEmpty()) {
            return 0;
        }

        List<AbstractInsnNode> replacement = parseReplacement(method);

        Set<AbstractInsnNode> matchedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AbstractInsnNode> matchHeads = Collections.newSetFromMap(new IdentityHashMap<>());

        for (InstructionPatternMatch match : matches) {
            for (AbstractInsnNode instruction : match.getInstructions()) {
                matchedSet.add(instruction);
            }
            if (!match.getInstructions().isEmpty()) {
                matchHeads.add(match.getInstructions().get(0));
            }
        }

        InsnList newList = new InsnList();
        for (AbstractInsnNode instruction : method.instructions) {
            if (!matchedSet.contains(instruction)) {
                newList.add(instruction.clone(new IdentityHashMap<>()));
            } else if (matchHeads.contains(instruction)) {
                for (AbstractInsnNode replacementInstruction : replacement) {
                    newList.add(replacementInstruction.clone(new IdentityHashMap<>()));
                }
            }
        }

        method.instructions = newList;
        return matches.size();
    }

    private List<AbstractInsnNode> parseReplacement(MethodNode context) {
        Map<String, LabelNode> existingLabels = new LinkedHashMap<>();
        for (AbstractInsnNode instruction : context.instructions) {
            if (instruction instanceof LabelNode) {
                existingLabels.put("L" + existingLabels.size(), (LabelNode) instruction);
            }
        }

        AssemblerClipboardCodec.ParsedInstructions parsed = AssemblerClipboardCodec.parse(
                replacementText,
                name -> {
                    LabelNode label = existingLabels.get(name);
                    return label != null ? label : new LabelNode();
                }
        );

        return parsed.instructions();
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }

    public float progress() {
        return methods.isEmpty() ? 1f : (float) methodIndex / methods.size();
    }

    public int methodsProcessed() {
        return methodIndex;
    }

    public int methodCount() {
        return methods.size();
    }

    public int totalReplacements() {
        return totalReplacements;
    }

    public int methodsModified() {
        return results.size();
    }

    public List<ReplaceResult> results() {
        return Collections.unmodifiableList(results);
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
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