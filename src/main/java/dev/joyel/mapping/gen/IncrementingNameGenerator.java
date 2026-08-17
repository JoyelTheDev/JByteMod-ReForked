package dev.joyel.mapping.gen;

import java.util.HashSet;
import java.util.Set;

public final class IncrementingNameGenerator implements NameGenerator {

    private final String classPrefix;
    private final String fieldPrefix;
    private final String methodPrefix;

    private int classCounter = 0;
    private int fieldCounter = 0;
    private int methodCounter = 0;

    private final Set<String> usedClassNames = new HashSet<String>();
    private final Set<String> usedFieldNames = new HashSet<String>();
    private final Set<String> usedMethodNames = new HashSet<String>();

    public IncrementingNameGenerator(String classPrefix, String fieldPrefix, String methodPrefix) {
        this.classPrefix = classPrefix;
        this.fieldPrefix = fieldPrefix;
        this.methodPrefix = methodPrefix;
    }

    public IncrementingNameGenerator() {
        this("Class_", "field_", "method_");
    }

    @Override
    public String nextClassName(String currentName) {
        String name;
        do {
            name = classPrefix + (++classCounter);
        } while (usedClassNames.contains(name));
        usedClassNames.add(name);
        return name;
    }

    @Override
    public String nextFieldName(String owner, String currentName, String desc) {
        String name;
        do {
            name = fieldPrefix + (++fieldCounter);
        } while (usedFieldNames.contains(name));
        usedFieldNames.add(name);
        return name;
    }

    @Override
    public String nextMethodName(String owner, String currentName, String desc) {
        String name;
        do {
            name = methodPrefix + (++methodCounter);
        } while (usedMethodNames.contains(name));
        usedMethodNames.add(name);
        return name;
    }

    @Override
    public void reset() {
        classCounter = 0;
        fieldCounter = 0;
        methodCounter = 0;
        usedClassNames.clear();
        usedFieldNames.clear();
        usedMethodNames.clear();
    }
}
