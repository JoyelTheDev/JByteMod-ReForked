package de.xbrowniecodez.jbytemod.jdwp.protocol;

public final class JdwpTag {

    private JdwpTag() {}

    public static final byte ARRAY = '[';
    public static final byte BYTE = 'B';
    public static final byte CHAR = 'C';
    public static final byte OBJECT = 'L';
    public static final byte FLOAT = 'F';
    public static final byte DOUBLE = 'D';
    public static final byte INT = 'I';
    public static final byte LONG = 'J';
    public static final byte SHORT = 'S';
    public static final byte VOID = 'V';
    public static final byte BOOLEAN = 'Z';
    public static final byte STRING = 's';
    public static final byte THREAD = 't';
    public static final byte THREAD_GROUP = 'g';
    public static final byte CLASS_LOADER = 'l';
    public static final byte CLASS_OBJECT = 'c';

    public static String tagName(byte tag) {
        switch (tag) {
            case ARRAY: return "array";
            case BYTE: return "byte";
            case CHAR: return "char";
            case OBJECT: return "object";
            case FLOAT: return "float";
            case DOUBLE: return "double";
            case INT: return "int";
            case LONG: return "long";
            case SHORT: return "short";
            case VOID: return "void";
            case BOOLEAN: return "boolean";
            case STRING: return "String";
            case THREAD: return "Thread";
            case THREAD_GROUP: return "ThreadGroup";
            case CLASS_LOADER: return "ClassLoader";
            case CLASS_OBJECT: return "Class";
            default: return "unknown(0x" + Integer.toHexString(tag & 0xFF) + ")";
        }
    }
}
