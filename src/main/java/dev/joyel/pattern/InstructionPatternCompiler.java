package dev.joyel.pattern;

import dev.joyel.assembler.AssemblerClipboardCodec;
import dev.joyel.assembler.AssemblerValueCodec;
import dev.joyel.assembler.OpcodeClasses;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.regex.Pattern;

public final class InstructionPatternCompiler {
    private static final Set<String> FRAME_TYPES = new HashSet<>(Arrays.asList(
            "F_NEW", "F_FULL", "F_APPEND", "F_CHOP", "F_SAME", "F_SAME1"
    ));

    private InstructionPatternCompiler() {}

    public static Compilation compile(String source, boolean includeMetadata) {
        String input = source == null ? "" : source;
        List<InstructionPattern.Element> elements = new ArrayList<>();
        List<PatternDiagnostic> diagnostics = new ArrayList<>();
        boolean ignoredMetadata = false;
        String[] lines = input.split("\\r?\\n|\\r", -1);

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String trimmed = lines[lineIndex].trim();
            int sourceLine = lineIndex + 1;

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            if (trimmed.equals("...")) {
                if (elements.isEmpty() || !(elements.get(elements.size() - 1) instanceof InstructionPattern.Gap)) {
                    elements.add(InstructionPattern.Gap.INSTANCE);
                }
                continue;
            }

            if (trimmed.equals("*")) {
                elements.add(InstructionPattern.AnyInstruction.INSTANCE);
                continue;
            }

            List<String> tokens;
            try {
                tokens = AssemblerClipboardCodec.tokenize(trimmed);
            } catch (IllegalArgumentException e) {
                diagnostics.add(error(sourceLine, 1, e.getMessage()));
                continue;
            }

            if (tokens.isEmpty()) {
                continue;
            }

            String opcode = tokens.get(0).toLowerCase(Locale.ROOT);
            Class<?> instructionClass = OpcodeClasses.getOpcodeClass(opcode);

            if (instructionClass == null) {
                diagnostics.add(error(sourceLine, 1, "Unknown opcode '" + tokens.get(0) + "'"));
                continue;
            }

            boolean metadata = isMetadata(instructionClass);
            if (metadata && !includeMetadata) {
                ignoredMetadata = true;
                continue;
            }

            try {
                List<OperandKind> kinds = operandKinds(opcode, instructionClass, tokens);
                List<OperandMatcher> operands = new ArrayList<>(kinds.size());

                for (int i = 0; i < kinds.size(); i++) {
                    operands.add(compileOperand(tokens.get(i + 1), kinds.get(i)));
                }

                elements.add(new InstructionPattern.InstructionLine(
                        sourceLine,
                        opcode,
                        Collections.unmodifiableList(operands)
                ));
            } catch (IllegalArgumentException e) {
                diagnostics.add(error(sourceLine, operandColumn(trimmed, tokens), e.getMessage()));
            }
        }

        if (ignoredMetadata) {
            diagnostics.add(new PatternDiagnostic(
                    1,
                    1,
                    PatternDiagnostic.Severity.WARNING,
                    "Metadata lines are ignored; enable Include Metadata to match them"
            ));
        }

        if (elements.isEmpty()) {
            diagnostics.add(error(1, 1, "Enter at least one instruction or wildcard"));
        } else {
            boolean allGaps = true;
            for (InstructionPattern.Element element : elements) {
                if (!(element instanceof InstructionPattern.Gap)) {
                    allGaps = false;
                    break;
                }
            }
            if (allGaps) {
                diagnostics.add(error(1, 1, "A pattern cannot contain only sequence gaps"));
            }
        }

        boolean valid = true;
        for (PatternDiagnostic diagnostic : diagnostics) {
            if (diagnostic.severity() == PatternDiagnostic.Severity.ERROR) {
                valid = false;
                break;
            }
        }

        InstructionPattern pattern = valid
                ? new InstructionPattern(input, includeMetadata, Collections.unmodifiableList(elements))
                : null;

        return new Compilation(pattern, Collections.unmodifiableList(diagnostics));
    }

    private static List<OperandKind> operandKinds(String opcode, Class<?> type, List<String> tokens) {
        int count = tokens.size() - 1;

        if (type == InsnNode.class) {
            return fixed(opcode, count);
        }

        if (type == IntInsnNode.class || type == VarInsnNode.class) {
            return fixed(opcode, count, OperandKind.INTEGER);
        }

        if (type == TypeInsnNode.class) {
            return fixed(opcode, count, OperandKind.STRING);
        }

        if (type == FieldInsnNode.class) {
            return fixed(opcode, count, OperandKind.STRING, OperandKind.STRING, OperandKind.STRING);
        }

        if (type == MethodInsnNode.class) {
            return fixed(opcode, count, OperandKind.STRING, OperandKind.STRING, OperandKind.STRING, OperandKind.BOOLEAN);
        }

        if (type == InvokeDynamicInsnNode.class) {
            return fixed(opcode, count, OperandKind.STRING, OperandKind.STRING, OperandKind.VALUE, OperandKind.VALUE_LIST);
        }

        if (type == JumpInsnNode.class || type == LabelNode.class) {
            return fixed(opcode, count, OperandKind.LABEL);
        }

        if (type == LdcInsnNode.class) {
            return fixed(opcode, count, OperandKind.VALUE);
        }

        if (type == IincInsnNode.class) {
            return fixed(opcode, count, OperandKind.INTEGER, OperandKind.INTEGER);
        }

        if (type == MultiANewArrayInsnNode.class) {
            return fixed(opcode, count, OperandKind.STRING, OperandKind.INTEGER);
        }

        if (type == LineNumberNode.class) {
            return fixed(opcode, count, OperandKind.INTEGER, OperandKind.LABEL);
        }

        if (type == FrameNode.class) {
            return fixed(opcode, count, OperandKind.FRAME_TYPE, OperandKind.FRAME_VALUES, OperandKind.FRAME_VALUES);
        }

        if (type == TableSwitchInsnNode.class) {
            if (count < 4) {
                throw new IllegalArgumentException("Expected min, max, default, and labels");
            }

            List<OperandKind> kinds = new ArrayList<>(count);
            kinds.add(OperandKind.INTEGER);
            kinds.add(OperandKind.INTEGER);

            for (int i = 2; i < count; i++) {
                kinds.add(OperandKind.LABEL);
            }

            return kinds;
        }

        if (type == LookupSwitchInsnNode.class) {
            if (count < 1 || (count - 1) % 2 != 0) {
                throw new IllegalArgumentException("Expected default followed by key/label pairs");
            }

            List<OperandKind> kinds = new ArrayList<>(count);
            kinds.add(OperandKind.LABEL);

            for (int i = 1; i < count; i += 2) {
                kinds.add(OperandKind.INTEGER);
                kinds.add(OperandKind.LABEL);
            }

            return kinds;
        }

        throw new IllegalArgumentException("Unsupported opcode '" + opcode + "'");
    }

    private static List<OperandKind> fixed(String opcode, int actual, OperandKind... kinds) {
        if (actual != kinds.length) {
            throw new IllegalArgumentException(
                    "Expected " + kinds.length + " operand" +
                    (kinds.length == 1 ? "" : "s") +
                    " for " + opcode + ", found " + actual
            );
        }
        return Arrays.asList(kinds);
    }

    private static OperandMatcher compileOperand(String token, OperandKind kind) {
        if (token.equals("*")) {
            return AnyOperand.INSTANCE;
        }

        if (kind == OperandKind.INTEGER) {
            return new ExactOperand(kind, decodeInteger(token));
        }

        if (kind == OperandKind.BOOLEAN) {
            return new ExactOperand(kind, decodeBoolean(token));
        }

        if (kind == OperandKind.STRING) {
            return StringOperand.compile(token);
        }

        if (kind == OperandKind.LABEL) {
            return new LabelOperand(decodePatternString(token).literal);
        }

        if (kind == OperandKind.VALUE) {
            return new ExactOperand(kind, AssemblerValueCodec.parse(token));
        }

        if (kind == OperandKind.VALUE_LIST) {
            return new ExactOperand(kind, AssemblerValueCodec.parseList(token));
        }

        if (kind == OperandKind.FRAME_TYPE) {
            String type = token.toUpperCase(Locale.ROOT);
            if (!FRAME_TYPES.contains(type)) {
                throw new IllegalArgumentException("Unknown frame type '" + token + "'");
            }
            return new ExactOperand(kind, type);
        }

        if (kind == OperandKind.FRAME_VALUES) {
            if (!token.equalsIgnoreCase("null") && !(token.startsWith("[") && token.endsWith("]"))) {
                throw new IllegalArgumentException("Expected null or a bracketed frame-value list");
            }
            return new ExactOperand(kind, token);
        }

        throw new IllegalArgumentException("Unhandled operand kind: " + kind);
    }

    private static boolean isMetadata(Class<?> type) {
        return type == LabelNode.class || type == FrameNode.class || type == LineNumberNode.class;
    }

    private static int operandColumn(String line, List<String> tokens) {
        if (tokens.size() < 2) {
            return Math.max(1, line.length());
        }
        return Math.max(1, line.indexOf(tokens.get(1)) + 1);
    }

    private static PatternDiagnostic error(int line, int column, String message) {
        return new PatternDiagnostic(
                line,
                column,
                PatternDiagnostic.Severity.ERROR,
                message == null ? "Invalid instruction" : message
        );
    }

    static int decodeInteger(String token) {
        try {
            return Integer.decode(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer '" + token + "'");
        }
    }

    private static boolean decodeBoolean(String token) {
        if (token.equalsIgnoreCase("true")) {
            return true;
        }
        if (token.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("Expected true or false");
    }

    private static PatternString decodePatternString(String token) {
        if (token.length() < 2 || token.charAt(0) != '"' || token.charAt(token.length() - 1) != '"') {
            throw new IllegalArgumentException("Expected a quoted string");
        }

        StringBuilder literal = new StringBuilder();
        StringBuilder regex = new StringBuilder("^");
        boolean wildcard = false;

        for (int i = 1; i < token.length() - 1; i++) {
            char c = token.charAt(i);

            if (c == '\\') {
                if (++i >= token.length() - 1) {
                    throw new IllegalArgumentException("Incomplete escape sequence");
                }

                char escaped = token.charAt(i);

                if (escaped == '*' || escaped == '?') {
                    literal.append(escaped);
                    regex.append(Pattern.quote(String.valueOf(escaped)));
                    continue;
                }

                char decoded;
                if (escaped == '\\' || escaped == '"') {
                    decoded = escaped;
                } else if (escaped == 'n') {
                    decoded = '\n';
                } else if (escaped == 'r') {
                    decoded = '\r';
                } else if (escaped == 't') {
                    decoded = '\t';
                } else if (escaped == 'b') {
                    decoded = '\b';
                } else if (escaped == 'f') {
                    decoded = '\f';
                } else if (escaped == 'u') {
                    if (i + 4 >= token.length()) {
                        throw new IllegalArgumentException("Incomplete unicode escape");
                    }
                    String digits = token.substring(i + 1, i + 5);
                    try {
                        decoded = (char) Integer.parseInt(digits, 16);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid unicode escape");
                    }
                    i += 4;
                } else {
                    throw new IllegalArgumentException("Unknown escape \\'" + escaped + "'");
                }

                literal.append(decoded);
                regex.append(Pattern.quote(String.valueOf(decoded)));
            } else if (c == '*') {
                wildcard = true;
                regex.append(".*");
            } else if (c == '?') {
                wildcard = true;
                regex.append('.');
            } else {
                literal.append(c);
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }

        regex.append('$');
        return new PatternString(literal.toString(), wildcard ? Pattern.compile(regex.toString(), Pattern.DOTALL) : null);
    }

    public static final class Compilation {
        private final InstructionPattern pattern;
        private final List<PatternDiagnostic> diagnostics;

        Compilation(InstructionPattern pattern, List<PatternDiagnostic> diagnostics) {
            this.pattern = pattern;
            this.diagnostics = diagnostics;
        }

        public boolean valid() {
            return pattern != null;
        }

        public InstructionPattern pattern() {
            return pattern;
        }

        public List<PatternDiagnostic> diagnostics() {
            return diagnostics;
        }

        public PatternDiagnostic primaryDiagnostic() {
            for (PatternDiagnostic diagnostic : diagnostics) {
                if (diagnostic.severity() == PatternDiagnostic.Severity.ERROR) {
                    return diagnostic;
                }
            }
            return diagnostics.isEmpty() ? null : diagnostics.get(0);
        }
    }

    public enum OperandKind {
        INTEGER,
        BOOLEAN,
        STRING,
        LABEL,
        VALUE,
        VALUE_LIST,
        FRAME_TYPE,
        FRAME_VALUES
    }

    public interface OperandMatcher {
        boolean matches(String candidate, Map<String, String> labels);
    }

    public enum AnyOperand implements OperandMatcher {
        INSTANCE;

        @Override
        public boolean matches(String candidate, Map<String, String> labels) {
            return true;
        }
    }

    public static final class LabelOperand implements OperandMatcher {
        private final String name;

        public LabelOperand(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean matches(String candidate, Map<String, String> labels) {
            String candidateName;
            try {
                candidateName = AssemblerValueCodec.parseQuotedString(candidate);
            } catch (IllegalArgumentException e) {
                return false;
            }

            String bound = labels.get(name);
            if (bound == null) {
                labels.put(name, candidateName);
                return true;
            }

            return bound.equals(candidateName);
        }
    }

    public static final class StringOperand implements OperandMatcher {
        private final String literal;
        private final Pattern glob;

        private StringOperand(String literal, Pattern glob) {
            this.literal = literal;
            this.glob = glob;
        }

        static StringOperand compile(String token) {
            PatternString value = decodePatternString(token);
            return new StringOperand(value.literal, value.glob);
        }

        @Override
        public boolean matches(String candidate, Map<String, String> labels) {
            String value;
            try {
                value = AssemblerValueCodec.parseQuotedString(candidate);
            } catch (IllegalArgumentException e) {
                return false;
            }

            return glob == null ? literal.equals(value) : glob.matcher(value).matches();
        }
    }

    public static final class ExactOperand implements OperandMatcher {
        private final OperandKind kind;
        private final Object value;

        public ExactOperand(OperandKind kind, Object value) {
            this.kind = kind;
            this.value = value;
        }

        @Override
        public boolean matches(String candidate, Map<String, String> labels) {
            try {
                Object other;
                if (kind == OperandKind.INTEGER) {
                    other = decodeInteger(candidate);
                } else if (kind == OperandKind.BOOLEAN) {
                    other = decodeBoolean(candidate);
                } else if (kind == OperandKind.VALUE) {
                    other = AssemblerValueCodec.parse(candidate);
                } else if (kind == OperandKind.VALUE_LIST) {
                    other = AssemblerValueCodec.parseList(candidate);
                } else if (kind == OperandKind.FRAME_TYPE) {
                    other = candidate.toUpperCase(Locale.ROOT);
                } else {
                    other = candidate;
                }

                if (value instanceof Object[] && other instanceof Object[]) {
                    return Arrays.deepEquals((Object[]) value, (Object[]) other);
                }

                return value == null ? other == null : value.equals(other);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        private static boolean decodeBoolean(String token) {
            if (token.equalsIgnoreCase("true")) {
                return true;
            }
            if (token.equalsIgnoreCase("false")) {
                return false;
            }
            throw new IllegalArgumentException("Expected true or false");
        }
    }

    private static final class PatternString {
        final String literal;
        final Pattern glob;

        PatternString(String literal, Pattern glob) {
            this.literal = literal;
            this.glob = glob;
        }
    }
}