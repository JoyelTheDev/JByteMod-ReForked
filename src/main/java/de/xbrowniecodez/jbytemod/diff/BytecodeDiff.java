package de.xbrowniecodez.jbytemod.diff;

import java.io.Serializable;

public class BytecodeDiff implements Serializable {

    public enum DiffType {
        ADDED,
        REMOVED,
        MODIFIED,
        CLASS_ACCESS_CHANGED,
        METHOD_ADDED,
        METHOD_REMOVED,
        FIELD_ADDED,
        FIELD_REMOVED
    }

    private final DiffType type;
    private final String className;
    private final String methodName;
    private final String methodDesc;
    private final int instructionIndex;
    private final String oldValue;
    private final String newValue;

    public BytecodeDiff(DiffType type, String className, String methodName,
                        String methodDesc, int instructionIndex,
                        String oldValue, String newValue) {
        this.type = type;
        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.instructionIndex = instructionIndex;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public DiffType getType() { return type; }
    public String getClassName() { return className; }
    public String getMethodName() { return methodName; }
    public String getMethodDesc() { return methodDesc; }
    public int getInstructionIndex() { return instructionIndex; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }

    @Override
    public String toString() {
        switch (type) {
            case ADDED:
                return "[+] " + className + "#" + methodName + methodDesc + " @" + instructionIndex + " " + newValue;
            case REMOVED:
                return "[-] " + className + "#" + methodName + methodDesc + " @" + instructionIndex + " " + oldValue;
            case MODIFIED:
                return "[~] " + className + "#" + methodName + methodDesc + " @" + instructionIndex + " " + oldValue + " -> " + newValue;
            case CLASS_ACCESS_CHANGED:
                return "[A] " + className + " access: " + oldValue + " -> " + newValue;
            case METHOD_ADDED:
                return "[M+] " + className + "#" + methodName + methodDesc;
            case METHOD_REMOVED:
                return "[M-] " + className + "#" + methodName + methodDesc;
            case FIELD_ADDED:
                return "[F+] " + className + "." + methodName + " " + methodDesc;
            case FIELD_REMOVED:
                return "[F-] " + className + "." + methodName + " " + methodDesc;
            default:
                return "[?] " + className;
        }
    }
}
