package dev.joyel.update;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile(
            "^[vV]?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
            "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" +
            "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    private static final Pattern NUMERIC_IDENTIFIER  = Pattern.compile("0|[1-9]\\d*");
    private static final Pattern NUMBERED_IDENTIFIER = Pattern.compile("^(.*?)(\\d+)$");

    private final BigInteger major;
    private final BigInteger minor;
    private final BigInteger patch;
    private final List<String> prerelease;
    private final String display;

    private SemanticVersion(Matcher matcher) {
        this.major = new BigInteger(matcher.group(1));
        this.minor = new BigInteger(matcher.group(2));
        this.patch = new BigInteger(matcher.group(3));
        String prereleaseText = matcher.group(4);
        if (prereleaseText == null) {
            this.prerelease = Collections.emptyList();
        } else {
            this.prerelease = Collections.unmodifiableList(
                    new ArrayList<String>(Arrays.asList(prereleaseText.split("\\."))));
        }
        StringBuilder sb = new StringBuilder()
                .append(major).append('.').append(minor).append('.').append(patch);
        if (prereleaseText != null) sb.append('-').append(prereleaseText);
        this.display = sb.toString();
    }

    public static SemanticVersion parse(String value) {
        if (value == null) return null;
        Matcher matcher = VERSION_PATTERN.matcher(value.trim());
        return matcher.matches() ? new SemanticVersion(matcher) : null;
    }

    public boolean isPrerelease() {
        return !prerelease.isEmpty();
    }

    public BigInteger getMajor() { return major; }
    public BigInteger getMinor() { return minor; }
    public BigInteger getPatch() { return patch; }

    @Override
    public int compareTo(SemanticVersion other) {
        int cmp = major.compareTo(other.major);
        if (cmp != 0) return cmp;
        cmp = minor.compareTo(other.minor);
        if (cmp != 0) return cmp;
        cmp = patch.compareTo(other.patch);
        if (cmp != 0) return cmp;

        if (prerelease.isEmpty()) return other.prerelease.isEmpty() ? 0 : 1;
        if (other.prerelease.isEmpty()) return -1;

        int length = Math.min(prerelease.size(), other.prerelease.size());
        for (int i = 0; i < length; i++) {
            cmp = compareIdentifier(prerelease.get(i), other.prerelease.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric  = NUMERIC_IDENTIFIER.matcher(left).matches();
        boolean rightNumeric = NUMERIC_IDENTIFIER.matcher(right).matches();

        if (leftNumeric && rightNumeric) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        if (leftNumeric != rightNumeric) return leftNumeric ? -1 : 1;

        Matcher lm = NUMBERED_IDENTIFIER.matcher(left);
        Matcher rm = NUMBERED_IDENTIFIER.matcher(right);
        if (lm.matches() && rm.matches() && lm.group(1).equals(rm.group(1))) {
            int cmp = new BigInteger(lm.group(2)).compareTo(new BigInteger(rm.group(2)));
            if (cmp != 0) return cmp;
        }
        return left.compareTo(right);
    }

    @Override
    public String toString() {
        return display;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SemanticVersion)) return false;
        return compareTo((SemanticVersion) o) == 0;
    }

    @Override
    public int hashCode() {
        int h = major.hashCode();
        h = 31 * h + minor.hashCode();
        h = 31 * h + patch.hashCode();
        h = 31 * h + prerelease.hashCode();
        return h;
    }
}
