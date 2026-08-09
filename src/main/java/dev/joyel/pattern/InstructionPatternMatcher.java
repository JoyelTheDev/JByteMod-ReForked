package dev.joyel.pattern;

import dev.joyel.assembler.AssemblerClipboardCodec;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class InstructionPatternMatcher {
    private InstructionPatternMatcher() {}

    public static List<InstructionPatternMatch> findAll(ClassNode owner, MethodNode method, InstructionPattern pattern) {
        List<Candidate> candidates = buildCandidates(method, pattern.includeMetadata());
        List<InstructionPatternMatch> matches = new ArrayList<InstructionPatternMatch>();
        for (int start = 0; start < candidates.size(); start++) {
            int end = matchAt(pattern, candidates, start);
            if (end <= start) continue;
            List<Candidate> matched = candidates.subList(start, end);
            List<AbstractInsnNode> instructions = new ArrayList<AbstractInsnNode>(matched.size());
            StringBuilder formattedSb = new StringBuilder();
            for (int i = 0; i < matched.size(); i++) {
                if (i > 0) formattedSb.append("\n");
                formattedSb.append(matched.get(i).formatted);
                instructions.add(matched.get(i).instruction);
            }
            matches.add(new InstructionPatternMatch(owner, method, instructions, formattedSb.toString()));
        }
        return matches;
    }

    private static int matchAt(InstructionPattern pattern, List<Candidate> candidates, int start) {
        List<State> states = new ArrayList<State>();
        states.add(new State(start, new HashMap<String, String>()));

        for (InstructionPattern.Element element : pattern.elements) {
            Map<StateKey, State> next = new LinkedHashMap<StateKey, State>();
            if (element instanceof InstructionPattern.Gap) {
                for (State state : states) {
                    for (int idx = state.index; idx <= candidates.size(); idx++) {
                        StateKey key = new StateKey(idx, state.labels);
                        if (!next.containsKey(key)) next.put(key, new State(idx, state.labels));
                    }
                }
            } else {
                for (State state : states) {
                    if (state.index >= candidates.size()) continue;
                    Map<String, String> labels = new HashMap<String, String>(state.labels);
                    boolean ok;
                    if (element instanceof InstructionPattern.AnyInstruction) {
                        ok = true;
                    } else {
                        ok = matches((InstructionPattern.InstructionLine) element, candidates.get(state.index), labels);
                    }
                    if (!ok) continue;
                    State candidate = new State(state.index + 1, Collections.unmodifiableMap(labels));
                    StateKey key = new StateKey(candidate.index, candidate.labels);
                    if (!next.containsKey(key)) next.put(key, candidate);
                }
            }
            if (next.isEmpty()) return -1;
            states = new ArrayList<State>(next.values());
        }

        int min = Integer.MAX_VALUE;
        for (State s : states) if (s.index < min) min = s.index;
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private static boolean matches(InstructionPattern.InstructionLine pattern, Candidate candidate, Map<String, String> labels) {
        List<String> tokens = candidate.tokens;
        if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase(pattern.opcode())) return false;
        if (tokens.size() - 1 != pattern.operands().size()) return false;
        for (int i = 0; i < pattern.operands().size(); i++) {
            if (!pattern.operands().get(i).matches(tokens.get(i + 1), labels)) return false;
        }
        return true;
    }

    private static List<Candidate> buildCandidates(MethodNode method, boolean includeMetadata) {
        final Map<LabelNode, String> labelCache = new IdentityHashMap<LabelNode, String>();
        final AssemblerClipboardCodec.LabelNamer namer = new AssemblerClipboardCodec.LabelNamer() {
            public String name(LabelNode label) {
                String n = labelCache.get(label);
                if (n == null) { n = "L" + labelCache.size(); labelCache.put(label, n); }
                return n;
            }
        };
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (AbstractInsnNode insn : method.instructions) {
            if (!includeMetadata && isMetadata(insn)) continue;
            String formatted = AssemblerClipboardCodec.formatInstruction(insn, namer);
            candidates.add(new Candidate(insn, formatted, AssemblerClipboardCodec.tokenize(formatted)));
        }
        return candidates;
    }

    private static boolean isMetadata(AbstractInsnNode insn) {
        return insn instanceof LabelNode || insn instanceof FrameNode || insn instanceof LineNumberNode;
    }

    private static final class Candidate {
        final AbstractInsnNode instruction;
        final String formatted;
        final List<String> tokens;
        Candidate(AbstractInsnNode instruction, String formatted, List<String> tokens) {
            this.instruction = instruction; this.formatted = formatted; this.tokens = tokens;
        }
    }

    private static final class State {
        final int index;
        final Map<String, String> labels;
        State(int index, Map<String, String> labels) { this.index = index; this.labels = labels; }
    }

    private static final class StateKey {
        final int index;
        final Map<String, String> labels;
        StateKey(int index, Map<String, String> labels) { this.index = index; this.labels = labels; }

        public boolean equals(Object o) {
            if (!(o instanceof StateKey)) return false;
            StateKey other = (StateKey) o;
            return index == other.index && labels.equals(other.labels);
        }

        public int hashCode() { return 31 * index + labels.hashCode(); }
    }
}
