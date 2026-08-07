package dev.joyel.pattern;

import java.util.List;

public final class InstructionPattern {
    private final String source;
    private final boolean includeMetadata;
    final List<Element> elements;

    InstructionPattern(String source, boolean includeMetadata, List<Element> elements) {
        this.source = source;
        this.includeMetadata = includeMetadata;
        this.elements = List.copyOf(elements);
    }

    public String source() {
        return source;
    }

    public boolean includeMetadata() {
        return includeMetadata;
    }

    public int instructionPatternCount() {
        return (int) elements.stream().filter(e -> !(e instanceof Gap)).count();
    }

    public sealed interface Element permits Gap, AnyInstruction, InstructionLine {
    }

    public enum Gap implements Element {
        INSTANCE
    }

    public enum AnyInstruction implements Element {
        INSTANCE
    }

    public record InstructionLine(int sourceLine, String opcode,
                                  List<InstructionPatternCompiler.OperandMatcher> operands) implements Element {
    }
}
