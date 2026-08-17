package dev.joyel.mapping.gen;

import java.util.HashSet;
import java.util.Set;

public final class AlphabetNameGenerator implements NameGenerator {

    private int classIndex = 0;
    private int fieldIndex = 0;
    private int methodIndex = 0;

    private final Set<String> usedClassNames = new HashSet<String>();
    private final Set<String> usedFieldNames = new HashSet<String>();
    private final Set<String> usedMethodNames = new HashSet<String>();

    @Override
    public String nextClassName(String currentName) {
        String name;
        do {
            name = toAlpha(classIndex++);
        } while (usedClassNames.contains(name));
        usedClassNames.add(name);
        return name;
    }

    @Override
    public String nextFieldName(String owner, String currentName, String desc) {
        String name;
        do {
            name = toAlpha(fieldIndex++);
        } while (usedFieldNames.contains(name));
        usedFieldNames.add(name);
        return name;
    }

    @Override
    public String nextMethodName(String owner, String currentName, String desc) {
        String name;
        do {
            name = toAlpha(methodIndex++);
        } while (usedMethodNames.contains(name));
        usedMethodNames.add(name);
        return name;
    }

    @Override
    public void reset() {
        classIndex = 0;
        fieldIndex = 0;
        methodIndex = 0;
        usedClassNames.clear();
        usedFieldNames.clear();
        usedMethodNames.clear();
    }

    private static String toAlpha(int index) {
        StringBuilder sb = new StringBuilder();
        index++;
        while (index > 0) {
            index--;
            sb.insert(0, (char) ('a' + index % 26));
            index /= 26;
        }
        return sb.toString();
    }
}
