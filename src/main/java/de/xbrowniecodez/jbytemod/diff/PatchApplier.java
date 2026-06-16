package de.xbrowniecodez.jbytemod.diff;

import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import org.objectweb.asm.tree.*;

import java.util.*;

public class PatchApplier {

    public static class PatchApplyResult {
        private final List<String> applied = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();

        public void applied(String msg) { applied.add(msg); }
        public void failed(String msg) { failed.add(msg); }
        public void skipped(String msg) { skipped.add(msg); }

        public List<String> getApplied() { return Collections.unmodifiableList(applied); }
        public List<String> getFailed() { return Collections.unmodifiableList(failed); }
        public List<String> getSkipped() { return Collections.unmodifiableList(skipped); }

        public boolean isSuccess() { return failed.isEmpty(); }

        public String getSummary() {
            return "Applied: " + applied.size() + " | Failed: " + failed.size() + " | Skipped: " + skipped.size();
        }
    }

    public PatchApplyResult apply(BytecodePatch patch, Map<String, ClassNode> classes) {
        PatchApplyResult result = new PatchApplyResult();

        for (BytecodePatch.ClassPatch cp : patch.getClassPatches()) {
            String className = cp.getClassName();
            ClassNode cn = classes.get(className);

            if (cn == null) {
                result.skipped("Class not found: " + className);
                continue;
            }

            if (cp.getAccessChange() != null) {
                cn.access = cp.getAccessChange();
                result.applied("Access changed: " + className);
            }

            applyFieldPatches(cp, cn, result, className);
            applyMethodPatches(cp, cn, classes, result, className);
        }

        return result;
    }

    private void applyFieldPatches(BytecodePatch.ClassPatch cp, ClassNode cn,
                                    PatchApplyResult result, String className) {
        for (String[] fr : cp.getFieldsRemoved()) {
            boolean removed = false;
            Iterator<FieldNode> it = cn.fields.iterator();
            while (it.hasNext()) {
                FieldNode fn = it.next();
                if (fn.name.equals(fr[0]) && fn.desc.equals(fr[1])) {
                    it.remove();
                    removed = true;
                    break;
                }
            }
            if (removed) {
                result.applied("Field removed: " + className + "." + fr[0]);
            } else {
                result.skipped("Field not found to remove: " + className + "." + fr[0]);
            }
        }

        for (String[] fa : cp.getFieldsAdded()) {
            boolean exists = false;
            for (FieldNode fn : cn.fields) {
                if (fn.name.equals(fa[0]) && fn.desc.equals(fa[1])) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                int access = Integer.parseInt(fa[2]);
                cn.fields.add(new FieldNode(access, fa[0], fa[1], null, null));
                result.applied("Field added: " + className + "." + fa[0]);
            } else {
                result.skipped("Field already exists: " + className + "." + fa[0]);
            }
        }
    }

    private void applyMethodPatches(BytecodePatch.ClassPatch cp, ClassNode cn,
                                     Map<String, ClassNode> classes,
                                     PatchApplyResult result, String className) {
        for (BytecodePatch.MethodPatch mp : cp.getMethodPatches()) {
            switch (mp.getType()) {
                case REMOVE_METHOD:
                    applyMethodRemoval(mp, cn, result, className);
                    break;
                case ADD_METHOD:
                    applyMethodAddition(mp, cn, classes, result, className);
                    break;
                case PATCH_INSTRUCTIONS:
                    applyInstructionPatches(mp, cn, result, className);
                    break;
            }
        }
    }

    private void applyMethodRemoval(BytecodePatch.MethodPatch mp, ClassNode cn,
                                     PatchApplyResult result, String className) {
        Iterator<MethodNode> it = cn.methods.iterator();
        while (it.hasNext()) {
            MethodNode mn = it.next();
            if (mn.name.equals(mp.getMethodName()) && mn.desc.equals(mp.getMethodDesc())) {
                it.remove();
                result.applied("Method removed: " + className + "#" + mp.getMethodName() + mp.getMethodDesc());
                return;
            }
        }
        result.skipped("Method not found to remove: " + className + "#" + mp.getMethodName());
    }

    private void applyMethodAddition(BytecodePatch.MethodPatch mp, ClassNode cn,
                                      Map<String, ClassNode> classes,
                                      PatchApplyResult result, String className) {
        for (MethodNode existing : cn.methods) {
            if (existing.name.equals(mp.getMethodName()) && existing.desc.equals(mp.getMethodDesc())) {
                result.skipped("Method already exists: " + className + "#" + mp.getMethodName());
                return;
            }
        }

        if (mp.getFullMethodBytes() != null) {
            try {
                ClassNode stub = BytecodeUtils.getClassNodeFromBytes(mp.getFullMethodBytes());
                for (MethodNode mn : stub.methods) {
                    if (mn.name.equals(mp.getMethodName()) && mn.desc.equals(mp.getMethodDesc())) {
                        cn.methods.add(mn);
                        result.applied("Method added: " + className + "#" + mp.getMethodName() + mp.getMethodDesc());
                        return;
                    }
                }
                result.failed("Method not found in stub bytes: " + className + "#" + mp.getMethodName());
            } catch (Exception e) {
                result.failed("Failed to deserialize method bytes: " + className + "#" + mp.getMethodName() + ": " + e.getMessage());
            }
        } else {
            result.skipped("No method bytes stored for: " + className + "#" + mp.getMethodName());
        }
    }

    private void applyInstructionPatches(BytecodePatch.MethodPatch mp, ClassNode cn,
                                          PatchApplyResult result, String className) {
        MethodNode target = null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(mp.getMethodName()) && mn.desc.equals(mp.getMethodDesc())) {
                target = mn;
                break;
            }
        }
        if (target == null) {
            result.skipped("Method not found for insn patch: " + className + "#" + mp.getMethodName());
            return;
        }

        List<AbstractInsnNode> realInsns = getRealInsns(target);

        List<BytecodePatch.InsnPatch> sorted = new ArrayList<>(mp.getInsnPatches());
        sorted.sort((a, b) -> Integer.compare(b.getIndex(), a.getIndex()));

        for (BytecodePatch.InsnPatch ip : sorted) {
            try {
                applyInsnPatch(ip, target, realInsns, result, className, mp.getMethodName());
            } catch (Exception e) {
                result.failed("Insn patch failed at @" + ip.getIndex() + " in " + className + "#" + mp.getMethodName() + ": " + e.getMessage());
            }
        }
    }

    private void applyInsnPatch(BytecodePatch.InsnPatch ip, MethodNode target,
                                 List<AbstractInsnNode> realInsns,
                                 PatchApplyResult result, String className, String methodName) {
        int idx = ip.getIndex();
        switch (ip.getOp()) {
            case DELETE: {
                if (idx < realInsns.size()) {
                    AbstractInsnNode toRemove = realInsns.get(idx);
                    target.instructions.remove(toRemove);
                    realInsns.remove(idx);
                    result.applied("Insn deleted @" + idx + " in " + className + "#" + methodName);
                } else {
                    result.skipped("Delete out of range @" + idx + " in " + className + "#" + methodName);
                }
                break;
            }
            case INSERT: {
                AbstractInsnNode newInsn = InsnDeserializer.deserialize(ip.getSerializedInsn());
                if (newInsn == null) {
                    result.failed("Could not deserialize insn: " + ip.getSerializedInsn());
                    return;
                }
                if (idx < realInsns.size()) {
                    AbstractInsnNode before = realInsns.get(idx);
                    target.instructions.insertBefore(before, newInsn);
                    realInsns.add(idx, newInsn);
                } else {
                    target.instructions.add(newInsn);
                    realInsns.add(newInsn);
                }
                result.applied("Insn inserted @" + idx + " in " + className + "#" + methodName);
                break;
            }
            case REPLACE: {
                if (idx < realInsns.size()) {
                    AbstractInsnNode newInsn = InsnDeserializer.deserialize(ip.getSerializedInsn());
                    if (newInsn == null) {
                        result.failed("Could not deserialize insn for replace: " + ip.getSerializedInsn());
                        return;
                    }
                    AbstractInsnNode old = realInsns.get(idx);
                    target.instructions.set(old, newInsn);
                    realInsns.set(idx, newInsn);
                    result.applied("Insn replaced @" + idx + " in " + className + "#" + methodName);
                } else {
                    result.skipped("Replace out of range @" + idx + " in " + className + "#" + methodName);
                }
                break;
            }
        }
    }

    private List<AbstractInsnNode> getRealInsns(MethodNode mn) {
        List<AbstractInsnNode> list = new ArrayList<>();
        if (mn.instructions == null) return list;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn.getOpcode() != -1) {
                list.add(insn);
            }
        }
        return list;
    }
}
