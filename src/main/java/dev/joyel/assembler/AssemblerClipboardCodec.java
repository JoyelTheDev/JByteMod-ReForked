package dev.joyel.assembler;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

import java.util.*;

public final class AssemblerClipboardCodec {
    private AssemblerClipboardCodec() {}

    public interface LabelNamer {
        String name(LabelNode label);
    }

    public interface LabelResolver {
        LabelNode resolve(String name);
    }

    public static String format(List<AbstractInsnNode> instructions, LabelNamer labelNamer) {
        LabelNames labels = new LabelNames(labelNamer);
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof LabelNode) labels.name((LabelNode) insn);
            for (LabelNode ref : collectReferencedLabels(insn)) labels.name(ref);
        }
        List<String> lines = new ArrayList<String>(instructions.size());
        for (AbstractInsnNode insn : instructions) lines.add(formatInstruction(insn, labels));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    public static String formatInstruction(AbstractInsnNode insn, LabelNamer labelNamer) {
        LabelNames labels = new LabelNames(labelNamer);
        if (insn instanceof LabelNode) labels.name((LabelNode) insn);
        for (LabelNode ref : collectReferencedLabels(insn)) labels.name(ref);
        return formatInstruction(insn, labels);
    }

    public static ParsedInstructions parse(String input, LabelResolver existingLabelResolver) {
        List<SourceLine> srcLines = sourceLines(input);
        Map<String, LabelNode> declared = new LinkedHashMap<String, LabelNode>();
        Map<LabelNode, String> labelNames = new IdentityHashMap<LabelNode, String>();

        for (SourceLine src : srcLines) {
            List<String> tokens = src.tokens;
            if (!tokens.get(0).equalsIgnoreCase("label")) continue;
            requireCount(tokens, 2, src.number);
            String name = parseString(tokens.get(1), src.number);
            LabelNode label = new LabelNode();
            if (declared.containsKey(name)) {
                throw lineError(src.number, "Duplicate label " + AssemblerValueCodec.quote(name));
            }
            declared.put(name, label);
            labelNames.put(label, name);
        }

        ParseLabels parseLabels = new ParseLabels(declared, existingLabelResolver, labelNames);
        List<AbstractInsnNode> instructions = new ArrayList<AbstractInsnNode>();
        for (SourceLine src : srcLines) {
            try {
                AbstractInsnNode insn = parseInstruction(src.tokens, parseLabels);
                if (insn != null) instructions.add(insn);
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("Line ")) throw e;
                throw lineError(src.number, e.getMessage());
            } catch (RuntimeException e) {
                throw lineError(src.number, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        List<LabelNode> externalLabels = parseLabels.createdExternalLabels();
        instructions.addAll(0, externalLabels);
        return new ParsedInstructions(Collections.unmodifiableList(instructions), labelNames);
    }

    private static String formatInstruction(AbstractInsnNode insn, LabelNames labels) {
        if (insn instanceof LabelNode) return "label " + quote(labels.name((LabelNode) insn));
        if (insn instanceof FrameNode) {
            FrameNode f = (FrameNode) insn;
            return "frame " + frameTypeName(f.type) + " " + formatFrameValues(f.local, labels)
                    + " " + formatFrameValues(f.stack, labels);
        }
        if (insn instanceof LineNumberNode) {
            LineNumberNode l = (LineNumberNode) insn;
            return "line " + l.line + " " + quote(labels.name(l.start));
        }

        String opcode = opcodeName(insn.getOpcode());
        if (insn instanceof InsnNode) return opcode;
        if (insn instanceof IntInsnNode) return opcode + " " + ((IntInsnNode) insn).operand;
        if (insn instanceof VarInsnNode) return opcode + " " + ((VarInsnNode) insn).var;
        if (insn instanceof TypeInsnNode) return opcode + " " + quote(((TypeInsnNode) insn).desc);
        if (insn instanceof FieldInsnNode) {
            FieldInsnNode f = (FieldInsnNode) insn;
            return opcode + " " + quote(f.owner) + " " + quote(f.name) + " " + quote(f.desc);
        }
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode m = (MethodInsnNode) insn;
            return opcode + " " + quote(m.owner) + " " + quote(m.name) + " " + quote(m.desc) + " " + m.itf;
        }
        if (insn instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode d = (InvokeDynamicInsnNode) insn;
            return opcode + " " + quote(d.name) + " " + quote(d.desc) + " "
                    + AssemblerValueCodec.format(d.bsm) + " " + AssemblerValueCodec.formatList(d.bsmArgs);
        }
        if (insn instanceof JumpInsnNode) return opcode + " " + quote(labels.name(((JumpInsnNode) insn).label));
        if (insn instanceof LdcInsnNode) return opcode + " " + AssemblerValueCodec.format(((LdcInsnNode) insn).cst);
        if (insn instanceof IincInsnNode) {
            IincInsnNode i = (IincInsnNode) insn;
            return opcode + " " + i.var + " " + i.incr;
        }
        if (insn instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode t = (TableSwitchInsnNode) insn;
            StringBuilder sb = new StringBuilder(opcode).append(' ').append(t.min).append(' ').append(t.max)
                    .append(' ').append(quote(labels.name(t.dflt)));
            for (LabelNode l : t.labels) sb.append(' ').append(quote(labels.name(l)));
            return sb.toString();
        }
        if (insn instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode l = (LookupSwitchInsnNode) insn;
            StringBuilder sb = new StringBuilder(opcode).append(' ').append(quote(labels.name(l.dflt)));
            for (int i = 0; i < l.keys.size(); i++) {
                sb.append(' ').append(l.keys.get(i)).append(' ').append(quote(labels.name(l.labels.get(i))));
            }
            return sb.toString();
        }
        if (insn instanceof MultiANewArrayInsnNode) {
            MultiANewArrayInsnNode m = (MultiANewArrayInsnNode) insn;
            return opcode + " " + quote(m.desc) + " " + m.dims;
        }
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

        Class type = OpcodeClasses.getOpcodeClass(name);
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
        if (type == JumpInsnNode.class) {
            requireCount(tokens, 2, -1);
            return new JumpInsnNode(opcode, labels.resolve(parseString(tokens.get(1), -1)));
        }
        if (type == LdcInsnNode.class) { requireCount(tokens, 2, -1); return new LdcInsnNode(AssemblerValueCodec.parse(tokens.get(1))); }
        if (type == IincInsnNode.class) { requireCount(tokens, 3, -1); return new IincInsnNode(integer(tokens.get(1)), integer(tokens.get(2))); }
        if (type == MultiANewArrayInsnNode.class) { requireCount(tokens, 3, -1); return new MultiANewArrayInsnNode(parseString(tokens.get(1), -1), integer(tokens.get(2))); }
        if (type == TableSwitchInsnNode.class) {
            if (tokens.size() < 4) throw new IllegalArgumentException("tableswitch requires min, max, default, labels");
            int min = integer(tokens.get(1));
            int max = integer(tokens.get(2));
            LabelNode dflt = labels.resolve(parseString(tokens.get(3), -1));
            List<LabelNode> cases = new ArrayList<LabelNode>();
            for (int i = 4; i < tokens.size(); i++) cases.add(labels.resolve(parseString(tokens.get(i), -1)));
            return new TableSwitchInsnNode(min, max, dflt, cases.toArray(new LabelNode[0]));
        }
        if (type == LookupSwitchInsnNode.class) {
            if (tokens.size() < 2 || (tokens.size() - 2) % 2 != 0)
                throw new IllegalArgumentException("lookupswitch expects default then key/label pairs");
            LabelNode dflt = labels.resolve(parseString(tokens.get(1), -1));
            int[] keys = new int[(tokens.size() - 2) / 2];
            LabelNode[] cases = new LabelNode[keys.length];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = integer(tokens.get(2 + i * 2));
                cases[i] = labels.resolve(parseString(tokens.get(3 + i * 2), -1));
            }
            return new LookupSwitchInsnNode(dflt, keys, cases);
        }
        throw new IllegalArgumentException("Unsupported opcode '" + name + "'");
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<String>();
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

    private static String quote(String s) { return AssemblerValueCodec.quote(s); }

    private static String opcodeName(int opcode) {
        if (opcode < 0 || opcode >= Printer.OPCODES.length)
            throw new IllegalArgumentException("Unknown opcode " + opcode);
        return Printer.OPCODES[opcode].toLowerCase(Locale.ROOT);
    }

    private static String frameTypeName(int type) {
        if (type == Opcodes.F_NEW) return "F_NEW";
        if (type == Opcodes.F_FULL) return "F_FULL";
        if (type == Opcodes.F_APPEND) return "F_APPEND";
        if (type == Opcodes.F_CHOP) return "F_CHOP";
        if (type == Opcodes.F_SAME) return "F_SAME";
        if (type == Opcodes.F_SAME1) return "F_SAME1";
        return "F_SAME";
    }

    private static int parseFrameType(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.equals("F_NEW")) return Opcodes.F_NEW;
        if (upper.equals("F_FULL")) return Opcodes.F_FULL;
        if (upper.equals("F_APPEND")) return Opcodes.F_APPEND;
        if (upper.equals("F_CHOP")) return Opcodes.F_CHOP;
        if (upper.equals("F_SAME")) return Opcodes.F_SAME;
        if (upper.equals("F_SAME1")) return Opcodes.F_SAME1;
        throw new IllegalArgumentException("Unknown frame type: " + name);
    }

    private static String formatFrameValues(List values, LabelNames labels) {
        if (values == null || values.isEmpty()) return "null";
        List<String> parts = new ArrayList<String>();
        for (Object v : values) {
            if (v instanceof Integer) parts.add(String.valueOf(v));
            else if (v instanceof String) parts.add(quote((String) v));
            else if (v instanceof LabelNode) parts.add(quote(labels.name((LabelNode) v)));
            else parts.add(String.valueOf(v));
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(parts.get(i));
        }
        return sb.append("]").toString();
    }

    private static List<Object> parseFrameValues(String token, ParseLabels labels) {
        if (token.equalsIgnoreCase("null")) return Collections.emptyList();
        if (!token.startsWith("[") || !token.endsWith("]"))
            throw new IllegalArgumentException("Expected null or [...]");
        String inner = token.substring(1, token.length() - 1).trim();
        if (inner.isEmpty()) return Collections.emptyList();
        List<Object> result = new ArrayList<Object>();
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
        List<LabelNode> refs = new ArrayList<LabelNode>();
        if (insn instanceof JumpInsnNode) refs.add(((JumpInsnNode) insn).label);
        if (insn instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode t = (TableSwitchInsnNode) insn;
            refs.add(t.dflt); refs.addAll(t.labels);
        }
        if (insn instanceof LookupSwitchInsnNode) {
            LookupSwitchInsnNode l = (LookupSwitchInsnNode) insn;
            refs.add(l.dflt); refs.addAll(l.labels);
        }
        if (insn instanceof LineNumberNode) refs.add(((LineNumberNode) insn).start);
        return refs;
    }

    private static List<SourceLine> sourceLines(String input) {
        List<SourceLine> lines = new ArrayList<SourceLine>();
        String[] raw = input.split("\\r?\\n|\\r", -1);
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
            String msg = "Expected " + (expected - 1) + " operand" + (expected == 2 ? "" : "s")
                    + " for " + tokens.get(0) + ", found " + (tokens.size() - 1);
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

    private static int size(List values) { return values == null ? 0 : values.size(); }
    private static Object[] array(List values) { return values == null || values.isEmpty() ? null : values.toArray(); }

    private static IllegalArgumentException lineError(int line, String msg) {
        return new IllegalArgumentException("Line " + line + ": " + (msg == null ? "invalid instruction" : msg));
    }

    private static final class LabelNames {
        private final LabelNamer namer;
        private final Map<LabelNode, String> cache = new IdentityHashMap<LabelNode, String>();

        LabelNames(LabelNamer namer) { this.namer = namer; }

        String name(LabelNode label) {
            String cached = cache.get(label);
            if (cached == null) { cached = namer.name(label); cache.put(label, cached); }
            return cached;
        }
    }

    private static final class ParseLabels {
        private final Map<String, LabelNode> declared;
        private final LabelResolver externalResolver;
        private final Map<LabelNode, String> labelNames;
        private final List<LabelNode> createdExternal = new ArrayList<LabelNode>();

        ParseLabels(Map<String, LabelNode> declared, LabelResolver externalResolver, Map<LabelNode, String> labelNames) {
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
            LabelNode external = externalResolver.resolve(name);
            if (!createdExternal.contains(external)) {
                createdExternal.add(external);
                labelNames.put(external, name);
            }
            return external;
        }

        List<LabelNode> createdExternalLabels() { return createdExternal; }
    }

    public static final class ParsedInstructions {
        private final List<AbstractInsnNode> instructions;
        private final Map<LabelNode, String> labelNames;

        public ParsedInstructions(List<AbstractInsnNode> instructions, Map<LabelNode, String> labelNames) {
            this.instructions = instructions;
            this.labelNames = labelNames;
        }

        public List<AbstractInsnNode> instructions() { return instructions; }
        public Map<LabelNode, String> labelNames() { return labelNames; }
    }

    private static final class SourceLine {
        final int number;
        final List<String> tokens;
        SourceLine(int number, List<String> tokens) { this.number = number; this.tokens = tokens; }
    }
}
