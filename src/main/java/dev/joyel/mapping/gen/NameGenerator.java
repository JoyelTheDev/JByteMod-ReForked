package dev.joyel.mapping.gen;

public interface NameGenerator {

    String nextClassName(String currentName);

    String nextFieldName(String owner, String currentName, String desc);

    String nextMethodName(String owner, String currentName, String desc);

    void reset();
}
