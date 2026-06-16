package de.xbrowniecodez.jbytemod.diff;

import java.io.*;
import java.util.*;

public class BytecodePatch implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String patchName;
    private final String targetJar;
    private final long createdAt;
    private final List<ClassPatch> classPatches = new ArrayList<>();
    private String description;

    public BytecodePatch(String patchName, String targetJar) {
        this.patchName = patchName;
        this.targetJar = targetJar;
        this.createdAt = System.currentTimeMillis();
    }

    public void addClassPatch(ClassPatch cp) {
        classPatches.add(cp);
    }

    public List<ClassPatch> getClassPatches() {
        return Collections.unmodifiableList(classPatches);
    }

    public String getPatchName() { return patchName; }
    public String getTargetJar() { return targetJar; }
    public long getCreatedAt() { return createdAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public static class ClassPatch implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String className;
        private Integer accessChange;
        private final List<MethodPatch> methodPatches = new ArrayList<>();
        private final List<String[]> fieldsAdded = new ArrayList<>();
        private final List<String[]> fieldsRemoved = new ArrayList<>();

        public ClassPatch(String className) {
            this.className = className;
        }

        public void setAccessChange(int oldAccess, int newAccess) {
            this.accessChange = newAccess;
        }

        public void addMethodPatch(MethodPatch mp) {
            methodPatches.add(mp);
        }

        public void addFieldAdded(String name, String desc, int access) {
            fieldsAdded.add(new String[]{name, desc, String.valueOf(access)});
        }

        public void addFieldRemoved(String name, String desc) {
            fieldsRemoved.add(new String[]{name, desc});
        }

        public String getClassName() { return className; }
        public Integer getAccessChange() { return accessChange; }
        public List<MethodPatch> getMethodPatches() { return Collections.unmodifiableList(methodPatches); }
        public List<String[]> getFieldsAdded() { return Collections.unmodifiableList(fieldsAdded); }
        public List<String[]> getFieldsRemoved() { return Collections.unmodifiableList(fieldsRemoved); }
    }

    public static class MethodPatch implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum MethodPatchType { ADD_METHOD, REMOVE_METHOD, PATCH_INSTRUCTIONS }

        private final String methodName;
        private final String methodDesc;
        private final MethodPatchType type;
        private byte[] fullMethodBytes;
        private final List<InsnPatch> insnPatches = new ArrayList<>();

        public MethodPatch(String methodName, String methodDesc, MethodPatchType type) {
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.type = type;
        }

        public void setFullMethodBytes(byte[] bytes) {
            this.fullMethodBytes = bytes;
        }

        public void addInsnPatch(InsnPatch ip) {
            insnPatches.add(ip);
        }

        public String getMethodName() { return methodName; }
        public String getMethodDesc() { return methodDesc; }
        public MethodPatchType getType() { return type; }
        public byte[] getFullMethodBytes() { return fullMethodBytes; }
        public List<InsnPatch> getInsnPatches() { return Collections.unmodifiableList(insnPatches); }
    }

    public static class InsnPatch implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum PatchOp { INSERT, DELETE, REPLACE }

        private final PatchOp op;
        private final int index;
        private final String serializedInsn;

        public InsnPatch(PatchOp op, int index, String serializedInsn) {
            this.op = op;
            this.index = index;
            this.serializedInsn = serializedInsn;
        }

        public PatchOp getOp() { return op; }
        public int getIndex() { return index; }
        public String getSerializedInsn() { return serializedInsn; }
    }

    public void save(File file) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(this);
        }
    }

    public static BytecodePatch load(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (BytecodePatch) ois.readObject();
        }
    }

    public String toTextFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("PATCH ").append(patchName).append("\n");
        sb.append("TARGET ").append(targetJar).append("\n");
        sb.append("CREATED ").append(new Date(createdAt)).append("\n");
        if (description != null && !description.isEmpty()) {
            sb.append("DESC ").append(description).append("\n");
        }
        sb.append("\n");
        for (ClassPatch cp : classPatches) {
            sb.append("CLASS ").append(cp.getClassName()).append("\n");
            if (cp.getAccessChange() != null) {
                sb.append("  ACCESS ").append(cp.getAccessChange()).append("\n");
            }
            for (String[] fa : cp.getFieldsAdded()) {
                sb.append("  FIELD_ADD ").append(fa[0]).append(" ").append(fa[1]).append("\n");
            }
            for (String[] fr : cp.getFieldsRemoved()) {
                sb.append("  FIELD_REMOVE ").append(fr[0]).append(" ").append(fr[1]).append("\n");
            }
            for (MethodPatch mp : cp.getMethodPatches()) {
                sb.append("  METHOD ").append(mp.getMethodName()).append(" ").append(mp.getMethodDesc())
                        .append(" ").append(mp.getType()).append("\n");
                for (InsnPatch ip : mp.getInsnPatches()) {
                    sb.append("    ").append(ip.getOp()).append(" @").append(ip.getIndex())
                            .append(" ").append(ip.getSerializedInsn()).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public void saveAsText(File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.print(toTextFormat());
        }
    }
}
