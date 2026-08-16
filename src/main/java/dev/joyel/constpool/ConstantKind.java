package dev.joyel.constpool;

public enum ConstantKind {
    STRING("String"),
    INTEGER("Integer"),
    LONG("Long"),
    FLOAT("Float"),
    DOUBLE("Double"),
    TYPE("Class / Type"),
    HANDLE("Handle"),
    CONST_DYNAMIC("ConstantDynamic"),
    ANNOTATION("Annotation");

    private final String label;

    ConstantKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
