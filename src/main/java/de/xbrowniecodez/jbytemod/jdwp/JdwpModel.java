package de.xbrowniecodez.jbytemod.jdwp;

import java.util.List;

public final class JdwpModel {

    private JdwpModel() {}

    public static final class VmInfo {
        public final String description;
        public final int jdwpMajor;
        public final int jdwpMinor;
        public final String vmVersion;
        public final String vmName;

        public VmInfo(String description, int jdwpMajor, int jdwpMinor, String vmVersion, String vmName) {
            this.description = description;
            this.jdwpMajor = jdwpMajor;
            this.jdwpMinor = jdwpMinor;
            this.vmVersion = vmVersion;
            this.vmName = vmName;
        }

        @Override
        public String toString() {
            return vmName + " " + vmVersion + " (JDWP " + jdwpMajor + "." + jdwpMinor + ")";
        }
    }

    public static final class RemoteClass {
        public final long typeId;
        public final int refTypeTag;
        public final String signature;
        public final int status;

        public RemoteClass(long typeId, int refTypeTag, String signature, int status) {
            this.typeId = typeId;
            this.refTypeTag = refTypeTag;
            this.signature = signature;
            this.status = status;
        }

        public String getClassName() {
            if (signature == null || signature.isEmpty()) return "<unknown>";
            if (signature.startsWith("L") && signature.endsWith(";")) {
                return signature.substring(1, signature.length() - 1).replace('/', '.');
            }
            return signature;
        }

        @Override
        public String toString() {
            return getClassName();
        }
    }

    public static final class RemoteMethod {
        public final long methodId;
        public final long classId;
        public final String name;
        public final String signature;
        public final int modifiers;

        public RemoteMethod(long methodId, long classId, String name, String signature, int modifiers) {
            this.methodId = methodId;
            this.classId = classId;
            this.name = name;
            this.signature = signature;
            this.modifiers = modifiers;
        }

        @Override
        public String toString() {
            return name + signature;
        }
    }

    public static final class RemoteThread {
        public final long threadId;
        public final String name;
        public volatile int status;
        public volatile int suspendStatus;

        public RemoteThread(long threadId, String name) {
            this.threadId = threadId;
            this.name = name;
        }

        public boolean isSuspended() {
            return suspendStatus != 0;
        }

        @Override
        public String toString() {
            return name + " (id=" + threadId + (isSuspended() ? ", SUSPENDED" : "") + ")";
        }
    }

    public static final class StackFrame {
        public final long frameId;
        public final long threadId;
        public final int frameIndex;
        public final long classId;
        public final long methodId;
        public final long codeIndex;
        public List<LocalVariable> locals;

        public StackFrame(long frameId, long threadId, int frameIndex, long classId, long methodId, long codeIndex) {
            this.frameId = frameId;
            this.threadId = threadId;
            this.frameIndex = frameIndex;
            this.classId = classId;
            this.methodId = methodId;
            this.codeIndex = codeIndex;
        }
    }

    public static final class LocalVariable {
        public final int slot;
        public final String name;
        public final String signature;
        public final Object value;

        public LocalVariable(int slot, String name, String signature, Object value) {
            this.slot = slot;
            this.name = name;
            this.signature = signature;
            this.value = value;
        }

        @Override
        public String toString() {
            return name + " : " + signature + " = " + (value != null ? value.toString() : "null");
        }
    }

    public static final class Location {
        public final int typeTag;
        public final long classId;
        public final long methodId;
        public final long index;

        public Location(int typeTag, long classId, long methodId, long index) {
            this.typeTag = typeTag;
            this.classId = classId;
            this.methodId = methodId;
            this.index = index;
        }
    }

    public static final class BreakpointRequest {
        public final int requestId;
        public final Location location;
        public final String className;
        public final String methodName;
        public final long codeIndex;

        public BreakpointRequest(int requestId, Location location, String className, String methodName, long codeIndex) {
            this.requestId = requestId;
            this.location = location;
            this.className = className;
            this.methodName = methodName;
            this.codeIndex = codeIndex;
        }

        @Override
        public String toString() {
            return className + "." + methodName + " @" + codeIndex + " [req=" + requestId + "]";
        }
    }

    public static final class BreakpointHit {
        public final int requestId;
        public final long threadId;
        public final Location location;

        public BreakpointHit(int requestId, long threadId, Location location) {
            this.requestId = requestId;
            this.threadId = threadId;
            this.location = location;
        }
    }

    public static final class LineEntry {
        public final long lineCodeIndex;
        public final int lineNumber;

        public LineEntry(long lineCodeIndex, int lineNumber) {
            this.lineCodeIndex = lineCodeIndex;
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            return "line " + lineNumber + " @" + lineCodeIndex;
        }
    }
}
