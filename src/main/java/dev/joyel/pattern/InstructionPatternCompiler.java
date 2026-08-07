package dev.joyel.pattern;

import dev.joyel.assembler.AssemblerClipboardCodec;
import dev.joyel.assembler.AssemblerValueCodec;
import dev.joyel.assembler.OpcodeClasses;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.regex.Pattern;

public final class InstructionPatternCompiler {
    private static final Set<String> FRAME_TYPES = Set.of("F_NEW", "F_FULL", "F_APPEND", "F_CHOP", "F_SAME", "F_SAME1");

    private InstructionPatternCompiler() {
    }

    public static Compilation compile(String source, boolean includeMetadata) {
        String input = source == null ? "" : source;
        List<InstructionPattern.Element> elements = new ArrayList<>();
        List<PatternDiagnostic> diagnostics = new ArrayList<>();
        boolean ignoredMetadata = false;
        String[] lines = input.split("\\R", -1);

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String trimmed = lines[lineIndex].trim();
            int sourceLine = lineIndex + 1;
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
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
            if (tokens.isEmpty()) continue;

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
                elements.add(new InstructionPattern.InstructionLine(sourceLine, opcode, List.copyOf(operands)));
            } catch (IllegalArgumentException e) {
                diagnostics.add(error(sourceLine, operandColumn(trimmed, tokens), e.getMessage()));
            }
        }

        if (ignoredMetadata) {
            diagnostics.add(new PatternDiagnostic(1, 1, PatternDiagnostic.Severity.WARNING,
                    "Metadata lines are ignored; enable Include Metadata to match them"));
        }
        if (elements.isEmpty()) {
            diagnostics.add(error(1, 1, "Enter at least one instruction or wildcard"));
        } else if (elements.stream().allMatch(e -> e instanceof InstructionPattern.Gap)) {
            diagnostics.add(error(1, 1, "A pattern cannot contain only sequence gaps"));
        }

        boolean valid = diagnostics.stream().noneMatch(d -> d.severity() == PatternDiagnostic.Severity.ERROR);
        InstructionPattern pattern = valid ? new InstructionPattern(input, includeMetadata, elements) : null;
        return new Compilation(pattern, List.copyOf(diagnostics));
    }

    public static String completionTemplate(String opcode) {
        Class<?> type = OpcodeClasses.getOpcodeClass(opcode);
        if (type == null || type == InsnNode.class) return opcode;
        if (type == IntInsnNode.class || type == VarInsnNode.class || type == TypeInsnNode.class
                || type == JumpInsnNode.class || type == LdcInsnNode.class) return opcode + " *";
        if (type == FieldInsnNode.class) return opcode + " * * *";
        if (type == MethodInsnNode.class || type == InvokeDynamicInsnNode.class) return opcode + " * * * *";
        if (type == IincInsnNode.class || type == MultiANewArrayInsnNode.class) return opcode + " * *";
        if (type == TableSwitchInsnNode.class) return opcode + " * * * *";
        if (type == LookupSwitchInsnNode.class) return opcode + " * * *";
        if (type == LabelNode.class) return "label *";
        if (type == LineNumberNode.class) return "line * *";
        if (type == FrameNode.class) return "frame * * *";
        return opcode;
    }

    private static List<OperandKind> operandKinds(String opcode, Class<?> type, List<String> tokens) {
        int count = tokens.size() - 1;
        if (type == InsnNode.class) return fixed(opcode, count);
        if (type == IntInsnNode.class || type == VarInsnNode.class) return fixed(opcode, count, OperandKind.INTEGER);
        if (type == TypeInsnNode.class) return fixed(opcode, count, OperandKind.STRING);
        if (type == FieldInsnNode.class) return fixed(opcode, count, OperandKind.STRING, OperandKind.STRING, OperandKind.STRING);
        if (type == MethodInsnNode.class) return fixed(opcode, count, OperandKind.STRING, OperandKind.STRING, OperandKind.STRING, OperandKind.BOOLEAN);
        if (type == InvokeDynamicInsnNode.class) return fixed(opcode, count, OperandKind.STRING, OperandKind.STRING, OperandKind.VALUE, OperandKind.VALUE_LIST);
        if (type == JumpInsnNode.class || type == LabelNode.class) return fixed(opcode, count, OperandKind.LABEL);
        if (type == LdcInsnNode.class) return fixed(opcode, count, OperandKind.VALUE);
        if (type == IincInsnNode.class) return fixed(opcode, count, OperandKind.INTEGER, OperandKind.INTEGER);
        if (type == MultiANewArrayInsnNode.class) return fixed(opcode, count, OperandKind.STRING, OperandKind.INTEGER);
        if (type == LineNumberNode.class) return fixed(opcode, count, OperandKind.INTEGER, OperandKind.LABEL);
        if (type == FrameNode.class) return fixed(opcode, count, OperandKind.FRAME_TYPE, OperandKind.FRAME_VALUES, OperandKind.FRAME_VALUES);
        if (type == TableSwitchInsnNode.class) {
            if (count < 4) throw new IllegalArgumentException("Expected min, max, default, and labels");
            if (!tokens.get(1).equals("*") && !tokens.get(2).equals("*")) {
                int min = decodeInteger(tokens.get(1));
                int max = decodeInteger(tokens.get(2));
                if (max < min) throw new IllegalArgumentException("Table switch max is below min");
                if (count != 3 + max - min + 1) throw new IllegalArgumentException("Table switch range requires " + (max - min + 1) + " labels");
            }
            List<OperandKind> kinds = new ArrayList<>(count);
            kinds.add(OperandKind.INTEGER);
            kinds.add(OperandKind.INTEGER);
            for (int i = 2; i < count; i++) kinds.add(OperandKind.LABEL);
            return kinds;
        }
        if (type == LookupSwitchInsnNode.class) {
            if (count < 1 || (count - 1) % 2 != 0) throw new IllegalArgumentException("Expected default followed by key/label pairs");
            List<OperandKind> kinds = new ArrayList<>(count);
            kinds.add(OperandKind.LABEL);
            for (int i = 1; i < count; i += 2) { kinds.add(OperandKind.INTEGER); kinds.add(OperandKind.LABEL); }
            return kinds;
        }
        throw new IllegalArgumentException("Unsupported opcode '" + opcode + "'");
    }

    private static List<OperandKind> fixed(String opcode, int actual, OperandKind... kinds) {
        if (actual != kinds.length) {
            throw new IllegalArgumentException("Expected " + kinds.length + " operand"
                    + (kinds.length == 1 ? "" : "s") + " for " + opcode + ", found " + actual);
        }
        return List.of(kinds);
    }

    private static OperandMatcher compileOperand(String token, OperandKind kind) {
        if (token.equals("*")) return AnyOperand.INSTANCE;
        return switch (kind) {
            case INTEGER -> new ExactOperand(kind, decodeInteger(token));
            case BOOLEAN -> new ExactOperand(kind, decodeBoolean(token));
            case STRING -> StringOperand.compile(token);
            case LABEL -> new LabelOperand(decodePatternString(token).literal());
            case VALUE -> new ExactOperand(kind, AssemblerValueCodec.parse(token));
            case VALUE_LIST -> new ExactOperand(kind, AssemblerValueCodec.parseList(token));
            case FRAME_TYPE -> {
                String t = token.toUpperCase(Locale.ROOT);
                if (!FRAME_TYPES.contains(t)) throw new IllegalArgumentException("Unknown frame type '" + token + "'");
                yield new ExactOperand(kind, t);
            }
            case FRAME_VALUES -> {
                if (!token.equalsIgnoreCase("null") && !(token.startsWith("[") && token.endsWith("]"))) {
                    throw new IllegalArgumentException("Expected null or a bracketed frame-value list");
                }
                yield new ExactOperand(kind, token);
            }
        };
    }

    private static boolean isMetadata(Class<?> type) {
        return type == LabelNode.class || type == FrameNode.class || type == LineNumberNode.class;
    }

    private static int operandColumn(String line, List<String> tokens) {
        if (tokens.size() < 2) return Math.max(1, line.length());
        return Math.max(1, line.indexOf(tokens.get(1)) + 1);
    }

    private static PatternDiagnostic error(int line, int column, String message) {
        return new PatternDiagnostic(line, column, PatternDiagnostic.Severity.ERROR,
                message == null ? "Invalid instruction" : message);
    }

    private static int decodeInteger(String token) {
        try { return Integer.decode(token); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer '" + token + "'"); }
    }

    private static boolean decodeBoolean(String token) {
        if (token.equalsIgnoreCase("true")) return true;
        if (token.equalsIgnoreCase("false")) return false;
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
                if (++i >= token.length() - 1) throw new IllegalArgumentException("Incomplete escape sequence");
                char esc = token.charAt(i);
                if (esc == '*' || esc == '?') {
                    literal.append(esc);
                    regex.append(Pattern.quote(String.valueOf(esc)));
                    continue;
                }
                char decoded = switch (esc) {
                    case '\\', '"' -> esc;
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'u' -> {
                        if (i + 4 >= token.length()) throw new IllegalArgumentException("Incomplete unicode escape");
                        String digits = token.substring(i + 1, i + 5);
                        try { yield (char) Integer.parseInt(digits, 16); }
                        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid unicode escape"); }
                        finally { i += 4; }
                    }
                    default -> throw new IllegalArgumentException("Unknown escape \\'" + esc + "'");
                };
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

    public record Compilation(InstructionPattern pattern, List<PatternDiagnostic> diagnostics) {
        public boolean valid() {
            return pattern != null;
        }

        public PatternDiagnostic primaryDiagnostic() {
            return diagnostics.stream()
                    .filter(d -> d.severity() == PatternDiagnostic.Severity.ERROR)
                    .findFirst()
                    .orElse(diagnostics.isEmpty() ? null : diagnostics.get(0));
        }
    }

    public enum OperandKind {
        INTEGER, BOOLEAN, STRING, LABEL, VALUE, VALUE_LIST, FRAME_TYPE, FRAME_VALUES
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

    public record LabelOperand(String name) implements OperandMatcher {
        @Override
        public boolean matches(String candidate, Map<String, String> labels) {
            String candidateName;
            try { candidateName = AssemblerValueCodec.parseQuotedString(candidate); }
            catch (IllegalArgumentException e) { return false; }
            String bound = labels.putIfAbsent(name, candidateName);
            return bound == null || bound.equals(candidateName);
        }
    }

    public record StringOperand(String literal, Pattern glob) implements OperandMatcher {
        static StringOperand compile(String token) {
            PatternString v = decodePatternString(token);
            return new StringOperand(v.literal(), v.glob());
        }

        @Override
        public boolean matches(String candidate, Map<String, String> labels) {
            String value;
            try { value = AssemblerValueCodec.parseQuotedString(candidate); }
            catch (IllegalArgumentException e) { return false; }
            return glob == null ? literal.equals(value) : glob.matcher(value).matches();
        }
    }

    public record ExactOperand(OperandKind kind, Object value) implements OperandMatcher {
        @Override
        public boolean matches(String candidate, Map<String, String> labels) {
            try {
                Object other = switch (kind) {
                    case INTEGER -> decodeInteger(candidate);
                    case BOOLEAN -> decodeBoolean(candidate);
                    case VALUE -> AssemblerValueCodec.parse(candidate);
                    case VALUE_LIST -> AssemblerValueCodec.parseList(candidate);
                    case FRAME_TYPE -> candidate.toUpperCase(Locale.ROOT);
                    case FRAME_VALUES -> candidate;
                    default -> candidate;
                };
                if (value instanceof Object[] left && other instanceof Object[] right) {
                    return Arrays.deepEquals(left, right);
                }
                return Objects.equals(value, other);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }

    private record PatternString(String literal, Pattern glob) {
    }
}
