package dev.joyel.nbridge;

public record OrphanExport(NativeLibrary library, NativeExport export, String javaClass, String javaMethod,
                           String arguments, boolean classInArchive, boolean methodNameInClass) {

    public String describe() {
        if (javaClass == null) {
            return "Unknown";
        }
        return javaClass + "." + javaMethod;
    }

    public String reason() {
        if (javaClass == null) {
            return "Name could not be decoded";
        }
        if (!classInArchive) {
            return "Class not in archive";
        }
        return methodNameInClass
                ? "Class found; no native method with this signature"
                : "Class found; no method with this name (renamed?)";
    }
}
