package dev.joyel.assembler;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AssemblerValueCodec {
    private AssemblerValueCodec() {
    }

    public static String format(Object value) {
        if (value instanceof Integer n) return "int(" + n + ")";
        if (value instanceof Long n) return "long(" + n + ")";
        if (value instanceof Float n) return "float(" + n + ")";
        if (value instanceof Double n) return "double(" + n + ")";
        if (value instanceof String s) return "string(" + quote(s) + ")";
        if (value instanceof Type t) return "type(" + quote(t.getDescriptor()) + ")";
        if (value instanceof Handle h) {
            return "handle(" + handleTagName(h.getTag()) + ", " + quote(h.getOwner()) + ", "
                    + quote(h.getName()) + ", " + quote(h.getDesc()) + ", " + h.isInterface() + ")";
        }
        if (value instanceof ConstantDynamic cd) {
            List<String> args = new ArrayList<>();
            for (int i = 0; i < cd.getBootstrapMethodArgumentCount(); i++) {
                args.add(format(cd.getBootstrapMethodArgument(i)));
            }
            return "condy(" + quote(cd.getName()) + ", " + quote(cd.getDescriptor()) + ", "
                    + format(cd.getBootstrapMethod()) + ", [" + String.join(", ", args) + "])";
        }
        throw new IllegalArgumentException("Unsupported constant type: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    public static String formatList(Object[] values) {
        List<String> out = new ArrayList<>();
        for (Object v : values) out.add(format(v));
        return "[" + String.join(", ", out) + "]";
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
        if (!(v instanceof Handle h)) throw new IllegalArgumentException("Expected handle(...)");
        return h;
    }

    public static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
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
        return switch (tag) {
            case Opcodes.H_GETFIELD -> "H_GETFIELD";
            case Opcodes.H_GETSTATIC -> "H_GETSTATIC";
            case Opcodes.H_PUTFIELD -> "H_PUTFIELD";
            case Opcodes.H_PUTSTATIC -> "H_PUTSTATIC";
            case Opcodes.H_INVOKEVIRTUAL -> "H_INVOKEVIRTUAL";
            case Opcodes.H_INVOKESTATIC -> "H_INVOKESTATIC";
            case Opcodes.H_INVOKESPECIAL -> "H_INVOKESPECIAL";
            case Opcodes.H_NEWINVOKESPECIAL -> "H_NEWINVOKESPECIAL";
            case Opcodes.H_INVOKEINTERFACE -> "H_INVOKEINTERFACE";
            default -> throw new IllegalArgumentException("Invalid handle tag: " + tag);
        };
    }

    private static int parseHandleTag(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "H_GETFIELD" -> Opcodes.H_GETFIELD;
            case "H_GETSTATIC" -> Opcodes.H_GETSTATIC;
            case "H_PUTFIELD" -> Opcodes.H_PUTFIELD;
            case "H_PUTSTATIC" -> Opcodes.H_PUTSTATIC;
            case "H_INVOKEVIRTUAL" -> Opcodes.H_INVOKEVIRTUAL;
            case "H_INVOKESTATIC" -> Opcodes.H_INVOKESTATIC;
            case "H_INVOKESPECIAL" -> Opcodes.H_INVOKESPECIAL;
            case "H_NEWINVOKESPECIAL" -> Opcodes.H_NEWINVOKESPECIAL;
            case "H_INVOKEINTERFACE" -> Opcodes.H_INVOKEINTERFACE;
            default -> throw new IllegalArgumentException("Unknown handle tag: " + name);
        };
    }

    private static final class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input == null ? "" : input;
        }

        Object parseValue() {
            String fn = identifier();
            expect('(');
            return switch (fn.toLowerCase(Locale.ROOT)) {
                case "int" -> {
                    String n = scalar();
                    expect(')');
                    yield Integer.decode(n);
                }
                case "long" -> {
                    String n = scalar();
                    expect(')');
                    yield Long.decode(stripSuffix(n, 'l'));
                }
                case "float" -> {
                    String n = scalar();
                    expect(')');
                    yield Float.valueOf(stripSuffix(n, 'f'));
                }
                case "double" -> {
                    String n = scalar();
                    expect(')');
                    yield Double.valueOf(stripSuffix(n, 'd'));
                }
                case "string" -> {
                    String s = string();
                    expect(')');
                    yield s;
                }
                case "type" -> {
                    String desc = string();
                    expect(')');
                    yield Type.getType(desc);
                }
                case "handle" -> {
                    int tag = parseHandleTag(identifier());
                    expect(',');
                    skipWhitespace();
                    String owner = string();
                    expect(',');
                    skipWhitespace();
                    String name = string();
                    expect(',');
                    skipWhitespace();
                    String desc = string();
                    expect(',');
                    skipWhitespace();
                    boolean itf = bool();
                    expect(')');
                    yield new Handle(tag, owner, name, desc, itf);
                }
                case "condy" -> {
                    String name = string();
                    expect(',');
                    skipWhitespace();
                    String desc = string();
                    expect(',');
                    skipWhitespace();
                    Handle bsm = (Handle) parseValue();
                    expect(',');
                    skipWhitespace();
                    List<Object> bsmArgs = parseList();
                    expect(')');
                    yield new ConstantDynamic(name, desc, bsm, bsmArgs.toArray());
                }
                default -> throw new IllegalArgumentException("Unknown value type: " + fn);
            };
        }

        List<Object> parseList() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(parseValue());
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    break;
                }
                expect(',');
            }
            return list;
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < input.length() && input.charAt(pos) != '"') {
                char c = input.charAt(pos++);
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= input.length()) throw new IllegalArgumentException("Incomplete escape");
                char esc = input.charAt(pos++);
                char decoded = switch (esc) {
                    case '\\', '"' -> esc;
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'u' -> {
                        if (pos + 4 > input.length()) throw new IllegalArgumentException("Incomplete unicode escape");
                        String digits = input.substring(pos, pos + 4);
                        pos += 4;
                        try {
                            yield (char) Integer.parseInt(digits, 16);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid unicode escape");
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown escape: \\" + esc);
                };
                sb.append(decoded);
            }
            expect('"');
            return sb.toString();
        }

        private String identifier() {
            skipWhitespace();
            int start = pos;
            while (pos < input.length() && Character.isLetterOrDigit(input.charAt(pos)) || pos < input.length() && input.charAt(pos) == '_') {
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
