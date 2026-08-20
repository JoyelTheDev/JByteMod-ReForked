package dev.joyel.pattern;

import dev.joyel.assembler.AssemblerClipboardCodec;
import org.objectweb.asm.tree.*;

import java.util.*;

public final class InstructionPatternMatcher {
    private InstructionPatternMatcher() {}

    public static List<InstructionPatternMatch> findAll(ClassNode owner, MethodNode method, InstructionPattern pattern) {
        List<Candidate> candidates = buildCandidates(method, pattern.includeMetadata());
        List<InstructionPatternMatch> matches = new ArrayList<>();

        for (int start = 0; start < candidates.size(); start++) {
            int end = matchAt(pattern, candidates, start);
            if (end <= start) {
                continue;
            }

            List<Candidate> matched = candidates.subList(start, end);
            List<AbstractInsnNode> instructions = new ArrayList<>(matched.size());
            StringBuilder formattedBuilder = new StringBuilder();

            for (int i = 0; i < matched.size(); i++) {
                if (i > 0) {
                    formattedBuilder.append("\n");
                }
                formattedBuilder.append(matched.get(i).formatted);
                instructions.add(matched.get(i).instruction);
            }

            matches.add(new InstructionPatternMatch(owner, method, instructions, formattedBuilder.toString()));
        }

        return matches;
    }

    private static int matchAt(InstructionPattern pattern, List<Candidate> candidates, int start) {
        List<State> states = new ArrayList<>();
        states.add(new State(start, new HashMap<>()));

        for (InstructionPattern.Element element : pattern.elements) {
            Map<StateKey, State> next = new LinkedHashMap<>();

            if (element instanceof InstructionPattern.Gap) {
                for (State state : states) {
                    for (int index = state.index; index <= candidates.size(); index++) {
                        StateKey key = new StateKey(index, state.labels);
                        if (!next.containsKey(key)) {
                            next.put(key, new State(index, state.labels));
                        }
                    }
                }
            } else {
                for (State state : states) {
                    if (state.index >= candidates.size()) {
                        continue;
                    }

                    Map<String, String> labels = new HashMap<>(state.labels);
                    boolean matches;

                    if (element instanceof InstructionPattern.AnyInstruction) {
                        matches = true;
                    } else {
                        matches = matches((InstructionPattern.InstructionLine) element, candidates.get(state.index), labels);
                    }

                    if (!matches) {
                        continue;
                    }

                    State candidate = new State(state.index + 1, Collections.unmodifiableMap(labels));
                    StateKey key = new StateKey(candidate.index, candidate.labels);
                    if (!next.containsKey(key)) {
                        next.put(key, candidate);
                    }
                }
            }

            if (next.isEmpty()) {
                return -1;
            }

            states = new ArrayList<>(next.values());
        }

        int minimum = Integer.MAX_VALUE;
        for (State state : states) {
            if (state.index < minimum) {
                minimum = state.index;
            }
        }

        return minimum == Integer.MAX_VALUE ? -1 : minimum;
    }

    private static boolean matches(InstructionPattern.InstructionLine pattern, Candidate candidate, Map<String, String> labels) {
        List<String> tokens = candidate.tokens;

        if (tokens.isEmpty() || !tokens.get(0).equalsIgnoreCase(pattern.opcode())) {
            return false;
        }

        if (tokens.size() - 1 != pattern.operands().size()) {
            return false;
        }

        for (int i = 0; i < pattern.operands().size(); i++) {
            if (!pattern.operands().get(i).matches(tokens.get(i + 1), labels)) {
                return false;
            }
        }

        return true;
    }

    private static List<Candidate> buildCandidates(MethodNode method, boolean includeMetadata) {
        Map<LabelNode, String> labelCache = new IdentityHashMap<>();
        AssemblerClipboardCodec.LabelNamer namer = label -> {
            String name = labelCache.get(label);
            if (name == null) {
                name = "L" + labelCache.size();
                labelCache.put(label, name);
            }
            return name;
        };

        List<Candidate> candidates = new ArrayList<>();

        for (AbstractInsnNode instruction : method.instructions) {
            if (!includeMetadata && isMetadata(instruction)) {
                continue;
            }

            String formatted = AssemblerClipboardCodec.formatInstruction(instruction, namer);
            candidates.add(new Candidate(instruction, formatted, AssemblerClipboardCodec.tokenize(formatted)));
        }

        return candidates;
    }

    private static boolean isMetadata(AbstractInsnNode instruction) {
        return instruction instanceof LabelNode ||
               instruction instanceof FrameNode ||
               instruction instanceof LineNumberNode;
    }

    private static final class Candidate {
        final AbstractInsnNode instruction;
        final String formatted;
        final List<String> tokens;

        Candidate(AbstractInsnNode instruction, String formatted, List<String> tokens) {
            this.instruction = instruction;
            this.formatted = formatted;
            this.tokens = tokens;
        }
    }

    private static final class State {
        final int index;
        final Map<String, String> labels;

        State(int index, Map<String, String> labels) {
            this.index = index;
            this.labels = labels;
        }
    }

    private static final class StateKey {
        final int index;
        final Map<String, String> labels;

        StateKey(int index, Map<String, String> labels) {
            this.index = index;
            this.labels = labels;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof StateKey)) {
                return false;
            }
            StateKey that = (StateKey) other;
            return index == that.index && labels.equals(that.labels);
        }

        @Override
        public int hashCode() {
            return 31 * index + labels.hashCode();
        }
    }
}