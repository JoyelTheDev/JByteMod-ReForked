package dev.joyel.nbridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JniNames {
    private JniNames() {
    }

    public record Demangled(String className, String methodName, String arguments) {
    }

    public static String shortName(String internalClassName, String methodName) {
        return "Java_" + mangle(internalClassName, true) + "_" + mangle(methodName, false);
    }

    public static String longName(String internalClassName, String methodName, String descriptor) {
        int open = descriptor.indexOf('(');
        int close = descriptor.indexOf(')');
        String arguments = open >= 0 && close > open ? descriptor.substring(open + 1, close) : "";
        return shortName(internalClassName, methodName) + "__" + mangle(arguments, true);
    }

    public static String mangleClass(String internalClassName) {
        return mangle(internalClassName, true);
    }

    public static String mangle(String text, boolean slashIsSeparator) {
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z' || value >= '0' && value <= '9') {
                out.append(value);
            } else if (value == '/' && slashIsSeparator) {
                out.append('_');
            } else if (value == '_') {
                out.append("_1");
            } else if (value == ';') {
                out.append("_2");
            } else if (value == '[') {
                out.append("_3");
            } else {
                out.append("_0").append(String.format(Locale.ROOT, "%04x", (int) value));
            }
        }
        return out.toString();
    }

    public static String normalizeExport(String name, NativeLibrary library) {
        String result = name;
        if ("PE".equals(library.format()) && library.bits() == 32) {
            int at = result.lastIndexOf('@');
            if (at > 0 && at < result.length() - 1 && result.substring(at + 1).chars().allMatch(Character::isDigit)) {
                result = result.substring(0, at);
            }
            if (result.startsWith("_")) {
                result = result.substring(1);
            }
        }
        return result;
    }

    public static Demangled demangle(String jniName) {
        if (!jniName.startsWith("Java_")) {
            return null;
        }
        List<Object> tokens = tokenize(jniName.substring(5));
        int split = -1;
        for (int index = 0; index + 1 < tokens.size(); index++) {
            if (tokens.get(index) == Separator.INSTANCE && tokens.get(index + 1) == Separator.INSTANCE) {
                split = index;
                break;
            }
        }
        List<Object> head = split >= 0 ? tokens.subList(0, split) : tokens;
        List<Object> tail = split >= 0 ? tokens.subList(split + 2, tokens.size()) : List.of();
        int last = -1;
        for (int index = 0; index < head.size(); index++) {
            if (head.get(index) == Separator.INSTANCE) {
                last = index;
            }
        }
        if (last <= 0 || last == head.size() - 1) {
            return null;
        }
        String className = render(head.subList(0, last), ".");
        String methodName = render(head.subList(last + 1, head.size()), "/");
        String arguments = split >= 0 ? render(tail, "/") : null;
        return new Demangled(className, methodName, arguments);
    }

    private enum Separator {
        INSTANCE
    }

    private static List<Object> tokenize(String text) {
        List<Object> tokens = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            char value = text.charAt(index);
            if (value != '_') {
                tokens.add(value);
                index++;
                continue;
            }
            if (index + 1 >= text.length()) {
                tokens.add(Separator.INSTANCE);
                index++;
                continue;
            }
            char next = text.charAt(index + 1);
            if (next == '1') {
                tokens.add('_');
                index += 2;
            } else if (next == '2') {
                tokens.add(';');
                index += 2;
            } else if (next == '3') {
                tokens.add('[');
                index += 2;
            } else if (next == '0' && index + 6 <= text.length() && isHex(text, index + 2, 4)) {
                tokens.add((char) Integer.parseInt(text.substring(index + 2, index + 6), 16));
                index += 6;
            } else {
                tokens.add(Separator.INSTANCE);
                index++;
            }
        }
        return tokens;
    }

    private static boolean isHex(String text, int start, int length) {
        for (int index = start; index < start + length; index++) {
            if (Character.digit(text.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String render(List<Object> tokens, String separator) {
        StringBuilder out = new StringBuilder();
        for (Object token : tokens) {
            if (token == Separator.INSTANCE) {
                out.append(separator);
            } else {
                out.append((char) (Character) token);
            }
        }
        return out.toString();
    }
}
