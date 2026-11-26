package dev.joyel.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VersionCompare {
    private VersionCompare() {
    }

    public static int compare(String left, String right) {
        Parsed a = parse(left);
        Parsed b = parse(right);
        int length = Math.max(a.numbers.size(), b.numbers.size());
        for (int index = 0; index < length; index++) {
            long x = index < a.numbers.size() ? a.numbers.get(index) : 0;
            long y = index < b.numbers.size() ? b.numbers.get(index) : 0;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        if (a.qualifier.isEmpty() && b.qualifier.isEmpty()) {
            return 0;
        }
        if (a.qualifier.isEmpty()) {
            return 1;
        }
        if (b.qualifier.isEmpty()) {
            return -1;
        }
        return a.qualifier.compareTo(b.qualifier);
    }

    public static boolean isNewer(String candidate, String installed) {
        return compare(candidate, installed) > 0;
    }

    private static Parsed parse(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("v")) {
            text = text.substring(1);
        }
        List<Long> numbers = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int start = index;
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (index == start) {
                break;
            }
            try {
                numbers.add(Long.parseLong(text.substring(start, Math.min(index, start + 17))));
            } catch (NumberFormatException failure) {
                numbers.add(0L);
            }
            if (index < text.length() && text.charAt(index) == '.') {
                index++;
            } else {
                break;
            }
        }
        String qualifier = text.substring(Math.min(index, text.length())).replaceFirst("^[-+_.\\s]+", "");
        return new Parsed(numbers, qualifier);
    }

    private record Parsed(List<Long> numbers, String qualifier) {
    }
}
