package dev.joyel.pattern;

import java.util.List;

public final class InstructionPattern {
    private final String source;
    private final boolean includeMetadata;
    final List<Element> elements;

    InstructionPattern(String source, boolean includeMetadata, List<Element> elements) {
        this.source = source;
        this.includeMetadata = includeMetadata;
        this.elements = elements;
    }

    public String source() { return source; }
    public boolean includeMetadata() { return includeMetadata; }

    public int instructionPatternCount() {
        int count = 0;
        for (Element e : elements) {
            if (!(e instanceof Gap)) count++;
        }
        return count;
    }

    public interface Element {}

    public static final class Gap implements Element {
        public static final Gap INSTANCE = new Gap();
        private Gap() {}
    }

    public static final class AnyInstruction implements Element {
        public static final AnyInstruction INSTANCE = new AnyInstruction();
        private AnyInstruction() {}
    }

    public static final class InstructionLine implements Element {
        private final int sourceLine;
        private final String opcode;
        private final List<InstructionPatternCompiler.OperandMatcher> operands;

        public InstructionLine(int sourceLine, String opcode, List<InstructionPatternCompiler.OperandMatcher> operands) {
            this.sourceLine = sourceLine;
            this.opcode = opcode;
            this.operands = operands;
        }

        public int sourceLine() { return sourceLine; }
        public String opcode() { return opcode; }
        public List<InstructionPatternCompiler.OperandMatcher> operands() { return operands; }
    }
}
