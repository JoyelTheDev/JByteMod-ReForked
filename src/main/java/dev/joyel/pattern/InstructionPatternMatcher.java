package dev.joyel.pattern;

import dev.joyel.assembler.AssemblerClipboardCodec;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class InstructionPatternMatcher {
    private InstructionPatternMatcher() {
    }

    public static List<InstructionPatternMatch> findAll(ClassNode owner, MethodNode method, InstructionPattern pattern) {
        List<Candidate> candidates = buildCandidates(method, pattern.includeMetadata());
        List<InstructionPatternMatch> matches = new ArrayList<>();
        for (int start = 0; start < candidates.size(); start++) {
            int end = matchAt(pattern, candidates, start);
            if (end <= start) continue;
            List<Candidate> matched = candidates.subList(start, end);
            List<AbstractInsnNode> instructions = matched.stream().map(Candidate::instruction).toList();
            String formatted = String.join("\n", matched.stream().map(Candidate::formatted).toList());
            matches.add(new InstructionPatternMatch(owner, method, instructions, formatted));
        }
        return matches;
    }

    private static int matchAt(InstructionPattern pattern, List<Candidate> candidates, int start) {
        List<State> states = List.of(new State(start, Map.of()));
        for (InstructionPattern.Element element : pattern.elements) {
            Map<StateKey, State> next = new LinkedHashMap<>();
            if (element instanceof InstructionPattern.Gap) {
                for (State state : states) {
                    for (int idx = state.index(); idx <= candidates.size(); idx++) {
                        next.putIfAbsent(new StateKey(idx, state.labels()), new State(idx, state.labels()));
                    }
                }
            } else {
                for (State state : states) {
                    if (state.index() >= candidates.size()) continue;
                    Map<String, String> labels = new LinkedHashMap<>(state.labels());
                    boolean ok = element instanceof InstructionPattern.AnyInstruction
                            || matches((InstructionPattern.InstructionLine) element, candidates.get(state.index()), labels);
                    if (!ok) continue;
                    State candidate = new State(state.index() + 1, Map.copyOf(labels));
                    next.putIfAbsent(new StateKey(candidate.index(), candidate.labels()), candidate);
                }
            }
            if (next.isEmpty()) return -1;
            states = new ArrayList<>(next.values());
        }
        return states.stream().mapToInt(State::index).min().orElse(-1);
    }

    private static boolean matches(InstructionPattern.InstructionLine pattern, Candidate candidate, Map<String, String> labels) {
        List<String> tokens = candidate.tokens();
        if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase(pattern.opcode())) return false;
        if (tokens.size() - 1 != pattern.operands().size()) return false;
        for (int i = 0; i < pattern.operands().size(); i++) {
            if (!pattern.operands().get(i).matches(tokens.get(i + 1), labels)) return false;
        }
        return true;
    }

    private static List<Candidate> buildCandidates(MethodNode method, boolean includeMetadata) {
        Map<LabelNode, String> labels = new IdentityHashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (!includeMetadata && isMetadata(insn)) continue;
            String formatted = AssemblerClipboardCodec.formatInstruction(insn,
                    label -> labels.computeIfAbsent(label, ignored -> "L" + labels.size()));
            candidates.add(new Candidate(insn, formatted, AssemblerClipboardCodec.tokenize(formatted)));
        }
        return candidates;
    }

    private static boolean isMetadata(AbstractInsnNode insn) {
        return insn instanceof LabelNode || insn instanceof FrameNode || insn instanceof LineNumberNode;
    }

    private record Candidate(AbstractInsnNode instruction, String formatted, List<String> tokens) {
    }

    private record State(int index, Map<String, String> labels) {
    }

    private record StateKey(int index, Map<String, String> labels) {
    }
}
