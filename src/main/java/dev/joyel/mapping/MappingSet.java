package dev.joyel.mapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MappingSet {

    private final Map<String, String> classMappings = new LinkedHashMap<String, String>();
    private final Map<MemberKey, String> fieldMappings = new LinkedHashMap<MemberKey, String>();
    private final Map<MemberKey, String> methodMappings = new LinkedHashMap<MemberKey, String>();

    public void putClass(String oldInternalName, String newInternalName) {
        if (oldInternalName == null || newInternalName == null) return;
        if (oldInternalName.equals(newInternalName)) return;
        classMappings.put(oldInternalName, newInternalName);
    }

    public void putField(String owner, String name, String desc, String newName) {
        if (owner == null || name == null || desc == null || newName == null) return;
        if (name.equals(newName)) return;
        fieldMappings.put(new MemberKey(owner, name, desc), newName);
    }

    public void putMethod(String owner, String name, String desc, String newName) {
        if (owner == null || name == null || desc == null || newName == null) return;
        if (name.equals(newName)) return;
        methodMappings.put(new MemberKey(owner, name, desc), newName);
    }

    public String getClass(String oldInternalName) {
        return classMappings.get(oldInternalName);
    }

    public String getField(String owner, String name, String desc) {
        return fieldMappings.get(new MemberKey(owner, name, desc));
    }

    public String getMethod(String owner, String name, String desc) {
        return methodMappings.get(new MemberKey(owner, name, desc));
    }

    public boolean hasClass(String oldInternalName) {
        return classMappings.containsKey(oldInternalName);
    }

    public boolean hasField(String owner, String name, String desc) {
        return fieldMappings.containsKey(new MemberKey(owner, name, desc));
    }

    public boolean hasMethod(String owner, String name, String desc) {
        return methodMappings.containsKey(new MemberKey(owner, name, desc));
    }

    public Map<String, String> getClassMappings() {
        return Collections.unmodifiableMap(classMappings);
    }

    public Map<MemberKey, String> getFieldMappings() {
        return Collections.unmodifiableMap(fieldMappings);
    }

    public Map<MemberKey, String> getMethodMappings() {
        return Collections.unmodifiableMap(methodMappings);
    }

    public void merge(MappingSet other) {
        classMappings.putAll(other.classMappings);
        fieldMappings.putAll(other.fieldMappings);
        methodMappings.putAll(other.methodMappings);
    }

    public void clear() {
        classMappings.clear();
        fieldMappings.clear();
        methodMappings.clear();
    }

    public boolean isEmpty() {
        return classMappings.isEmpty() && fieldMappings.isEmpty() && methodMappings.isEmpty();
    }

    public int totalSize() {
        return classMappings.size() + fieldMappings.size() + methodMappings.size();
    }
}
