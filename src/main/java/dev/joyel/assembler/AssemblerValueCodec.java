package dev.joyel.assembler;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AssemblerValueCodec {
    private AssemblerValueCodec() {}

    public static String format(Object value) {
        if (value instanceof Integer) return "int(" + value + ")";
        if (value instanceof Long) return "long(" + value + ")";
        if (value instanceof Float) return "float(" + value + ")";
        if (value instanceof Double) return "double(" + value + ")";
        if (value instanceof String) return "string(" + quote((String) value) + ")";
        if (value instanceof Type) return "type(" + quote(((Type) value).getDescriptor()) + ")";
        if (value instanceof Handle) {
            Handle h = (Handle) value;
            return "handle(" + handleTagName(h.getTag()) + ", " + quote(h.getOwner()) + ", "
                    + quote(h.getName()) + ", " + quote(h.getDesc()) + ", " + h.isInterface() + ")";
        }
        if (value instanceof ConstantDynamic) {
            ConstantDynamic cd = (ConstantDynamic) value;
            List<String> args = new ArrayList<String>();
            for (int i = 0; i < cd.getBootstrapMethodArgumentCount(); i++) {
                args.add(format(cd.getBootstrapMethodArgument(i)));
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(args.get(i));
            }
            return "condy(" + quote(cd.getName()) + ", " + quote(cd.getDescriptor()) + ", "
                    + format(cd.getBootstrapMethod()) + ", [" + sb + "])";
        }
        throw new IllegalArgumentException("Unsupported constant type: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    public static String formatList(Object[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(format(values[i]));
        }
        return sb.append("]").toString();
    }

    public static Object parse(String input) {
        Parser p = new Parser(input);
        Object v = p.parseValue();
        p.requireEnd();
        return v;
    }

    public static Object[] parseList(String input) {
        Parser p = new Parser(input);
        List<Object> values = p.parseList();
        p.requireEnd();
        return values.toArray();
    }

    public static Handle parseHandle(String input) {
        Object v = parse(input);
        if (!(v instanceof Handle)) throw new IllegalArgumentException("Expected handle(...)");
        return (Handle) v;
    }

    public static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c == '\b') sb.append("\\b");
            else if (c == '\f') sb.append("\\f");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    public static String parseQuotedString(String input) {
        Parser p = new Parser(input);
        String v = p.string();
        p.requireEnd();
        return v;
    }

    private static String handleTagName(int tag) {
        if (tag == Opcodes.H_GETFIELD) return "H_GETFIELD";
        if (tag == Opcodes.H_GETSTATIC) return "H_GETSTATIC";
        if (tag == Opcodes.H_PUTFIELD) return "H_PUTFIELD";
        if (tag == Opcodes.H_PUTSTATIC) return "H_PUTSTATIC";
        if (tag == Opcodes.H_INVOKEVIRTUAL) return "H_INVOKEVIRTUAL";
        if (tag == Opcodes.H_INVOKESTATIC) return "H_INVOKESTATIC";
        if (tag == Opcodes.H_INVOKESPECIAL) return "H_INVOKESPECIAL";
        if (tag == Opcodes.H_NEWINVOKESPECIAL) return "H_NEWINVOKESPECIAL";
        if (tag == Opcodes.H_INVOKEINTERFACE) return "H_INVOKEINTERFACE";
        throw new IllegalArgumentException("Invalid handle tag: " + tag);
    }

    private static int parseHandleTag(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.equals("H_GETFIELD")) return Opcodes.H_GETFIELD;
        if (upper.equals("H_GETSTATIC")) return Opcodes.H_GETSTATIC;
        if (upper.equals("H_PUTFIELD")) return Opcodes.H_PUTFIELD;
        if (upper.equals("H_PUTSTATIC")) return Opcodes.H_PUTSTATIC;
        if (upper.equals("H_INVOKEVIRTUAL")) return Opcodes.H_INVOKEVIRTUAL;
        if (upper.equals("H_INVOKESTATIC")) return Opcodes.H_INVOKESTATIC;
        if (upper.equals("H_INVOKESPECIAL")) return Opcodes.H_INVOKESPECIAL;
        if (upper.equals("H_NEWINVOKESPECIAL")) return Opcodes.H_NEWINVOKESPECIAL;
        if (upper.equals("H_INVOKEINTERFACE")) return Opcodes.H_INVOKEINTERFACE;
        throw new IllegalArgumentException("Unknown handle tag: " + name);
    }

    static final class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input == null ? "" : input;
        }

        Object parseValue() {
            String fn = identifier();
            expect('(');
            String lower = fn.toLowerCase(Locale.ROOT);
            if (lower.equals("int")) {
                String n = scalar(); expect(')'); return Integer.decode(n);
            }
            if (lower.equals("long")) {
                String n = scalar(); expect(')'); return Long.decode(stripSuffix(n, 'l'));
            }
            if (lower.equals("float")) {
                String n = scalar(); expect(')'); return Float.valueOf(stripSuffix(n, 'f'));
            }
            if (lower.equals("double")) {
                String n = scalar(); expect(')'); return Double.valueOf(stripSuffix(n, 'd'));
            }
            if (lower.equals("string")) {
                String s = string(); expect(')'); return s;
            }
            if (lower.equals("type")) {
                String desc = string(); expect(')'); return Type.getType(desc);
            }
            if (lower.equals("handle")) {
                int tag = parseHandleTag(identifier());
                expect(','); skipWhitespace();
                String owner = string();
                expect(','); skipWhitespace();
                String name = string();
                expect(','); skipWhitespace();
                String desc = string();
                expect(','); skipWhitespace();
                boolean itf = bool();
                expect(')');
                return new Handle(tag, owner, name, desc, itf);
            }
            if (lower.equals("condy")) {
                String name = string();
                expect(','); skipWhitespace();
                String desc = string();
                expect(','); skipWhitespace();
                Handle bsm = (Handle) parseValue();
                expect(','); skipWhitespace();
                List<Object> bsmArgs = parseList();
                expect(')');
                return new ConstantDynamic(name, desc, bsm, bsmArgs.toArray());
            }
            throw new IllegalArgumentException("Unknown value type: " + fn);
        }

        List<Object> parseList() {
            expect('[');
            List<Object> list = new ArrayList<Object>();
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                if (peek() == ']') { pos++; break; }
                expect(',');
            }
            return list;
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < input.length() && input.charAt(pos) != '"') {
                char c = input.charAt(pos++);
                if (c != '\\') { sb.append(c); continue; }
                if (pos >= input.length()) throw new IllegalArgumentException("Incomplete escape");
                char esc = input.charAt(pos++);
                char decoded;
                if (esc == '\\' || esc == '"') decoded = esc;
                else if (esc == 'n') decoded = '\n';
                else if (esc == 'r') decoded = '\r';
                else if (esc == 't') decoded = '\t';
                else if (esc == 'b') decoded = '\b';
                else if (esc == 'f') decoded = '\f';
                else if (esc == 'u') {
                    if (pos + 4 > input.length()) throw new IllegalArgumentException("Incomplete unicode escape");
                    String digits = input.substring(pos, pos + 4);
                    pos += 4;
                    try { decoded = (char) Integer.parseInt(digits, 16); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid unicode escape"); }
                } else throw new IllegalArgumentException("Unknown escape: \\" + esc);
                sb.append(decoded);
            }
            expect('"');
            return sb.toString();
        }

        private String identifier() {
            skipWhitespace();
            int start = pos;
            while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
                pos++;
            }
            if (start == pos) throw new IllegalArgumentException("Expected identifier at pos " + pos);
            return input.substring(start, pos);
        }

        private String scalar() {
            skipWhitespace();
            int start = pos;
            char first = pos < input.length() ? input.charAt(pos) : 0;
            if (first == '-' || first == '+') pos++;
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == 'x' || c == 'X') pos++;
                else break;
            }
            return input.substring(start, pos);
        }

        private boolean bool() {
            String id = identifier();
            if (id.equalsIgnoreCase("true")) return true;
            if (id.equalsIgnoreCase("false")) return false;
            throw new IllegalArgumentException("Expected true or false");
        }

        private char peek() {
            return pos < input.length() ? input.charAt(pos) : 0;
        }

        private void expect(char c) {
            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at pos " + pos);
            }
            pos++;
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
        }

        void requireEnd() {
            skipWhitespace();
            if (pos != input.length()) {
                throw new IllegalArgumentException("Unexpected trailing text at pos " + pos);
            }
        }

        private static String stripSuffix(String s, char suffix) {
            if (!s.isEmpty() && Character.toLowerCase(s.charAt(s.length() - 1)) == suffix) {
                return s.substring(0, s.length() - 1);
            }
            return s;
        }
    }
}
