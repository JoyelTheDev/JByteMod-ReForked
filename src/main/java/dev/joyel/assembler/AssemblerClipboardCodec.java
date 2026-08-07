package dev.joyel.assembler;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

import java.util.*;
import java.util.function.Function;

public final class AssemblerClipboardCodec {
    private AssemblerClipboardCodec() {
    }

    public static String format(List<AbstractInsnNode> instructions, Function<LabelNode, String> labelNamer) {
        LabelNames labels = new LabelNames(labelNamer);
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof LabelNode l) labels.name(l);
            collectReferencedLabels(insn).forEach(labels::name);
        }
        List<String> lines = new ArrayList<>(instructions.size());
        for (AbstractInsnNode insn : instructions) lines.add(formatInstruction(insn, labels));
        return String.join("\n", lines);
    }

    public static String formatInstruction(AbstractInsnNode insn, Function<LabelNode, String> labelNamer) {
        LabelNames labels = new LabelNames(labelNamer);
        if (insn instanceof LabelNode l) labels.name(l);
        collectReferencedLabels(insn).forEach(labels::name);
        return formatInstruction(insn, labels);
    }

    public static ParsedInstructions parse(String input, Function<String, LabelNode> existingLabelResolver) {
        List<SourceLine> srcLines = sourceLines(input);
        Map<String, LabelNode> declared = new LinkedHashMap<>();
        Map<LabelNode, String> labelNames = new IdentityHashMap<>();

        for (SourceLine src : srcLines) {
            List<String> tokens = src.tokens();
            if (!tokens.get(0).equalsIgnoreCase("label")) continue;
            requireCount(tokens, 2, src.number());
            String name = parseString(tokens.get(1), src.number());
            LabelNode label = new LabelNode();
            if (declared.putIfAbsent(name, label) != null) {
                throw lineError(src.number(), "Duplicate label " + AssemblerValueCodec.quote(name));
            }
            labelNames.put(label, name);
        }

        ParseLabels labels = new ParseLabels(declared, existingLabelResolver, labelNames);
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (SourceLine src : srcLines) {
            try {
                AbstractInsnNode insn = parseInstruction(src.tokens(), labels);
                if (insn != null) instructions.add(insn);
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("Line ")) throw e;
                throw lineError(src.number(), e.getMessage());
            } catch (RuntimeException e) {
                throw lineError(src.number(), e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        instructions.addAll(0, labels.createdExternalLabels());
        return new ParsedInstructions(List.copyOf(instructions), labelNames);
    }

    private static String formatInstruction(AbstractInsnNode insn, LabelNames labels) {
        if (insn instanceof LabelNode l) return "label " + quote(labels.name(l));
        if (insn instanceof FrameNode f) {
            return "frame " + frameTypeName(f.type) + " " + formatFrameValues(f.local, labels)
                    + " " + formatFrameValues(f.stack, labels);
        }
        if (insn instanceof LineNumberNode l) return "line " + l.line + " " + quote(labels.name(l.start));

        String opcode = opcodeName(insn.getOpcode());
        if (insn instanceof InsnNode) return opcode;
        if (insn instanceof IntInsnNode i) return opcode + " " + i.operand;
        if (insn instanceof VarInsnNode v) return opcode + " " + v.var;
        if (insn instanceof TypeInsnNode t) return opcode + " " + quote(t.desc);
        if (insn instanceof FieldInsnNode f) return opcode + " " + quote(f.owner) + " " + quote(f.name) + " " + quote(f.desc);
        if (insn instanceof MethodInsnNode m) return opcode + " " + quote(m.owner) + " " + quote(m.name) + " " + quote(m.desc) + " " + m.itf;
        if (insn instanceof InvokeDynamicInsnNode d) return opcode + " " + quote(d.name) + " " + quote(d.desc) + " " + AssemblerValueCodec.format(d.bsm) + " " + AssemblerValueCodec.formatList(d.bsmArgs);
        if (insn instanceof JumpInsnNode j) return opcode + " " + quote(labels.name(j.label));
        if (insn instanceof LdcInsnNode l) return opcode + " " + AssemblerValueCodec.format(l.cst);
        if (insn instanceof IincInsnNode i) return opcode + " " + i.var + " " + i.incr;
        if (insn instanceof TableSwitchInsnNode t) {
            StringBuilder sb = new StringBuilder(opcode).append(' ').append(t.min).append(' ').append(t.max).append(' ').append(quote(labels.name(t.dflt)));
            for (LabelNode l : t.labels) sb.append(' ').append(quote(labels.name(l)));
            return sb.toString();
        }
        if (insn instanceof LookupSwitchInsnNode l) {
            StringBuilder sb = new StringBuilder(opcode).append(' ').append(quote(labels.name(l.dflt)));
            for (int i = 0; i < l.keys.size(); i++) sb.append(' ').append(l.keys.get(i)).append(' ').append(quote(labels.name(l.labels.get(i))));
            return sb.toString();
        }
        if (insn instanceof MultiANewArrayInsnNode m) return opcode + " " + quote(m.desc) + " " + m.dims;
        throw new IllegalArgumentException("Unsupported instruction node " + insn.getClass().getName());
    }

    private static AbstractInsnNode parseInstruction(List<String> tokens, ParseLabels labels) {
        String name = tokens.get(0).toLowerCase(Locale.ROOT);
        if (name.equals("label")) {
            requireCount(tokens, 2, -1);
            return labels.declared(parseString(tokens.get(1), -1));
        }
        if (name.equals("frame")) {
            requireCount(tokens, 4, -1);
            List<Object> locals = parseFrameValues(tokens.get(2), labels);
            List<Object> stack = parseFrameValues(tokens.get(3), labels);
            return new FrameNode(parseFrameType(tokens.get(1)), size(locals), array(locals), size(stack), array(stack));
        }
        if (name.equals("line")) {
            requireCount(tokens, 3, -1);
            return new LineNumberNode(integer(tokens.get(1)), labels.resolve(parseString(tokens.get(2), -1)));
        }

        Class<?> type = OpcodeClasses.getOpcodeClass(name);
        if (type == null) throw new IllegalArgumentException("Unknown opcode '" + tokens.get(0) + "'");
        int opcode = OpcodeClasses.getOpcodeIndex(name);
        if (type == InsnNode.class) { requireCount(tokens, 1, -1); return new InsnNode(opcode); }
        if (type == IntInsnNode.class) { requireCount(tokens, 2, -1); return new IntInsnNode(opcode, integer(tokens.get(1))); }
        if (type == VarInsnNode.class) { requireCount(tokens, 2, -1); return new VarInsnNode(opcode, integer(tokens.get(1))); }
        if (type == TypeInsnNode.class) { requireCount(tokens, 2, -1); return new TypeInsnNode(opcode, parseString(tokens.get(1), -1)); }
        if (type == FieldInsnNode.class) {
            requireCount(tokens, 4, -1);
            return new FieldInsnNode(opcode, parseString(tokens.get(1), -1), parseString(tokens.get(2), -1), parseString(tokens.get(3), -1));
        }
        if (type == MethodInsnNode.class) {
            requireCount(tokens, 5, -1);
            return new MethodInsnNode(opcode, parseString(tokens.get(1), -1), parseString(tokens.get(2), -1), parseString(tokens.get(3), -1), bool(tokens.get(4)));
        }
        if (type == InvokeDynamicInsnNode.class) {
            requireCount(tokens, 5, -1);
            Handle bsm = AssemblerValueCodec.parseHandle(tokens.get(3));
            return new InvokeDynamicInsnNode(parseString(tokens.get(1), -1), parseString(tokens.get(2), -1), bsm, AssemblerValueCodec.parseList(tokens.get(4)));
        }
        if (type == JumpInsnNode.class) { requireCount(tokens, 2, -1); return new JumpInsnNode(opcode, labels.resolve(parseString(tokens.get(1), -1))); }
        if (type == LdcInsnNode.class) { requireCount(tokens, 2, -1); return new LdcInsnNode(AssemblerValueCodec.parse(tokens.get(1))); }
        if (type == IincInsnNode.class) { requireCount(tokens, 3, -1); return new IincInsnNode(integer(tokens.get(1)), integer(tokens.get(2))); }
        if (type == MultiANewArrayInsnNode.class) { requireCount(tokens, 3, -1); return new MultiANewArrayInsnNode(parseString(tokens.get(1), -1), integer(tokens.get(2))); }
        if (type == TableSwitchInsnNode.class) {
            if (tokens.size() < 4) throw new IllegalArgumentException("tableswitch requires min, max, default, labels");
            int min = integer(tokens.get(1));
            int max = integer(tokens.get(2));
            LabelNode dflt = labels.resolve(parseString(tokens.get(3), -1));
            List<LabelNode> cases = new ArrayList<>();
            for (int i = 4; i < tokens.size(); i++) cases.add(labels.resolve(parseString(tokens.get(i), -1)));
            return new TableSwitchInsnNode(min, max, dflt, cases.toArray(new LabelNode[0]));
        }
        if (type == LookupSwitchInsnNode.class) {
            if (tokens.size() < 2 || (tokens.size() - 2) % 2 != 0) throw new IllegalArgumentException("lookupswitch expects default then key/label pairs");
            LabelNode dflt = labels.resolve(parseString(tokens.get(1), -1));
            List<Integer> keys = new ArrayList<>();
            List<LabelNode> cases = new ArrayList<>();
            for (int i = 2; i < tokens.size(); i += 2) {
                keys.add(integer(tokens.get(i)));
                cases.add(labels.resolve(parseString(tokens.get(i + 1), -1)));
            }
            return new LookupSwitchInsnNode(dflt, keys.stream().mapToInt(Integer::intValue).toArray(), cases.toArray(new LabelNode[0]));
        }
        throw new IllegalArgumentException("Unsupported opcode '" + name + "'");
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '"') {
                StringBuilder sb = new StringBuilder("\"");
                i++;
                while (i < line.length()) {
                    char ch = line.charAt(i++);
                    sb.append(ch);
                    if (ch == '\\' && i < line.length()) sb.append(line.charAt(i++));
                    else if (ch == '"') break;
                }
                tokens.add(sb.toString());
            } else if (c == '[') {
                int depth = 0;
                int start = i;
                while (i < line.length()) {
                    char ch = line.charAt(i++);
                    if (ch == '[') depth++;
                    else if (ch == ']') { depth--; if (depth == 0) break; }
                }
                tokens.add(line.substring(start, i));
            } else {
                int start = i;
                while (i < line.length() && !Character.isWhitespace(line.charAt(i))) i++;
                tokens.add(line.substring(start, i));
            }
        }
        return tokens;
    }

    private static String quote(String s) {
        return AssemblerValueCodec.quote(s);
    }

    private static String opcodeName(int opcode) {
        if (opcode < 0 || opcode >= Printer.OPCODES.length) throw new IllegalArgumentException("Unknown opcode " + opcode);
        return Printer.OPCODES[opcode].toLowerCase(Locale.ROOT);
    }

    private static String frameTypeName(int type) {
        return switch (type) {
            case Opcodes.F_NEW -> "F_NEW";
            case Opcodes.F_FULL -> "F_FULL";
            case Opcodes.F_APPEND -> "F_APPEND";
            case Opcodes.F_CHOP -> "F_CHOP";
            case Opcodes.F_SAME -> "F_SAME";
            case Opcodes.F_SAME1 -> "F_SAME1";
            default -> "F_SAME";
        };
    }

    private static int parseFrameType(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "F_NEW" -> Opcodes.F_NEW;
            case "F_FULL" -> Opcodes.F_FULL;
            case "F_APPEND" -> Opcodes.F_APPEND;
            case "F_CHOP" -> Opcodes.F_CHOP;
            case "F_SAME" -> Opcodes.F_SAME;
            case "F_SAME1" -> Opcodes.F_SAME1;
            default -> throw new IllegalArgumentException("Unknown frame type: " + name);
        };
    }

    private static String formatFrameValues(List<?> values, LabelNames labels) {
        if (values == null || values.isEmpty()) return "null";
        List<String> parts = new ArrayList<>();
        for (Object v : values) {
            if (v instanceof Integer i) parts.add(String.valueOf(i));
            else if (v instanceof String s) parts.add(quote(s));
            else if (v instanceof LabelNode l) parts.add(quote(labels.name(l)));
            else parts.add(String.valueOf(v));
        }
        return "[" + String.join(", ", parts) + "]";
    }

    private static List<Object> parseFrameValues(String token, ParseLabels labels) {
        if (token.equalsIgnoreCase("null")) return Collections.emptyList();
        if (!token.startsWith("[") || !token.endsWith("]")) throw new IllegalArgumentException("Expected null or [...]");
        String inner = token.substring(1, token.length() - 1).trim();
        if (inner.isEmpty()) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (String part : inner.split(",")) {
            String p = part.trim();
            if (p.startsWith("\"")) result.add(AssemblerValueCodec.parseQuotedString(p));
            else {
                try { result.add(Integer.parseInt(p)); }
                catch (NumberFormatException e) { result.add(p); }
            }
        }
        return result;
    }

    private static List<LabelNode> collectReferencedLabels(AbstractInsnNode insn) {
        List<LabelNode> refs = new ArrayList<>();
        if (insn instanceof JumpInsnNode j) refs.add(j.label);
        if (insn instanceof TableSwitchInsnNode t) { refs.add(t.dflt); refs.addAll(t.labels); }
        if (insn instanceof LookupSwitchInsnNode l) { refs.add(l.dflt); refs.addAll(l.labels); }
        if (insn instanceof LineNumberNode l) refs.add(l.start);
        return refs;
    }

    private static List<SourceLine> sourceLines(String input) {
        List<SourceLine> lines = new ArrayList<>();
        String[] raw = input.split("\\R", -1);
        for (int i = 0; i < raw.length; i++) {
            String trimmed = raw[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            List<String> tokens = tokenize(trimmed);
            if (!tokens.isEmpty()) lines.add(new SourceLine(i + 1, tokens));
        }
        return lines;
    }

    private static void requireCount(List<String> tokens, int expected, int line) {
        if (tokens.size() != expected) {
            String msg = "Expected " + (expected - 1) + " operand" + (expected == 2 ? "" : "s") + " for " + tokens.get(0) + ", found " + (tokens.size() - 1);
            if (line > 0) throw lineError(line, msg);
            throw new IllegalArgumentException(msg);
        }
    }

    private static String parseString(String token, int line) {
        try { return AssemblerValueCodec.parseQuotedString(token); }
        catch (IllegalArgumentException e) {
            if (line > 0) throw lineError(line, e.getMessage());
            throw e;
        }
    }

    private static int integer(String token) {
        try { return Integer.decode(token); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid integer '" + token + "'"); }
    }

    private static boolean bool(String token) {
        if (token.equalsIgnoreCase("true")) return true;
        if (token.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("Expected true or false");
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static Object[] array(List<?> values) {
        return values == null || values.isEmpty() ? null : values.toArray();
    }

    private static IllegalArgumentException lineError(int line, String msg) {
        return new IllegalArgumentException("Line " + line + ": " + (msg == null ? "invalid instruction" : msg));
    }

    private static final class LabelNames {
        private final Function<LabelNode, String> namer;
        private final Map<LabelNode, String> cache = new IdentityHashMap<>();

        LabelNames(Function<LabelNode, String> namer) {
            this.namer = namer;
        }

        String name(LabelNode label) {
            return cache.computeIfAbsent(label, namer::apply);
        }
    }

    private static final class ParseLabels {
        private final Map<String, LabelNode> declared;
        private final Function<String, LabelNode> externalResolver;
        private final Map<LabelNode, String> labelNames;
        private final List<LabelNode> createdExternal = new ArrayList<>();

        ParseLabels(Map<String, LabelNode> declared, Function<String, LabelNode> externalResolver, Map<LabelNode, String> labelNames) {
            this.declared = declared;
            this.externalResolver = externalResolver;
            this.labelNames = labelNames;
        }

        LabelNode declared(String name) {
            LabelNode l = declared.get(name);
            if (l == null) throw new IllegalArgumentException("Undefined label: " + name);
            return l;
        }

        LabelNode resolve(String name) {
            if (declared.containsKey(name)) return declared.get(name);
            LabelNode external = externalResolver.apply(name);
            if (!createdExternal.contains(external)) {
                createdExternal.add(external);
                labelNames.put(external, name);
            }
            return external;
        }

        List<LabelNode> createdExternalLabels() {
            return createdExternal;
        }
    }

    public record ParsedInstructions(List<AbstractInsnNode> instructions, Map<LabelNode, String> labelNames) {
    }

    private record SourceLine(int number, List<String> tokens) {
    }
}
