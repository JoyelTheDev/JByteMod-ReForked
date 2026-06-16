package de.xbrowniecodez.jbytemod.diff;

import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import org.objectweb.asm.tree.*;

import java.util.*;

public class PatchBuilder {

    public BytecodePatch buildFromDiff(DiffResult diff, Map<String, ClassNode> modifiedClasses, String patchName) {
        BytecodePatch patch = new BytecodePatch(patchName, diff.getOriginalJar());

        Map<String, BytecodePatch.ClassPatch> classPatchMap = new LinkedHashMap<>();

        for (BytecodeDiff d : diff.getDiffs()) {
            String className = d.getClassName();
            BytecodePatch.ClassPatch cp = classPatchMap.computeIfAbsent(className, BytecodePatch.ClassPatch::new);

            switch (d.getType()) {
                case CLASS_ACCESS_CHANGED:
                    cp.setAccessChange(0, Integer.parseInt(d.getNewValue()));
                    break;

                case FIELD_ADDED: {
                    FieldNode fn = findField(modifiedClasses, className, d.getMethodName(), d.getMethodDesc());
                    if (fn != null) cp.addFieldAdded(fn.name, fn.desc, fn.access);
                    break;
                }
                case FIELD_REMOVED:
                    cp.addFieldRemoved(d.getMethodName(), d.getMethodDesc());
                    break;

                case METHOD_ADDED: {
                    MethodNode mn = findMethod(modifiedClasses, className, d.getMethodName(), d.getMethodDesc());
                    if (mn != null) {
                        BytecodePatch.MethodPatch mp = new BytecodePatch.MethodPatch(
                                d.getMethodName(), d.getMethodDesc(),
                                BytecodePatch.MethodPatch.MethodPatchType.ADD_METHOD);
                        ClassNode stub = buildStubClass(className, mn, modifiedClasses);
                        if (stub != null) mp.setFullMethodBytes(BytecodeUtils.getClassNodeBytes(stub));
                        getOrCreateMethodPatch(cp, mn, BytecodePatch.MethodPatch.MethodPatchType.ADD_METHOD);
                    }
                    break;
                }
                case METHOD_REMOVED: {
                    BytecodePatch.MethodPatch mp = new BytecodePatch.MethodPatch(
                            d.getMethodName(), d.getMethodDesc(),
                            BytecodePatch.MethodPatch.MethodPatchType.REMOVE_METHOD);
                    cp.addMethodPatch(mp);
                    break;
                }

                case ADDED: {
                    BytecodePatch.MethodPatch mp = getOrCreateMethodPatch(cp, d.getMethodName(), d.getMethodDesc(),
                            BytecodePatch.MethodPatch.MethodPatchType.PATCH_INSTRUCTIONS);
                    mp.addInsnPatch(new BytecodePatch.InsnPatch(
                            BytecodePatch.InsnPatch.PatchOp.INSERT, d.getInstructionIndex(), d.getNewValue()));
                    break;
                }
                case REMOVED: {
                    BytecodePatch.MethodPatch mp = getOrCreateMethodPatch(cp, d.getMethodName(), d.getMethodDesc(),
                            BytecodePatch.MethodPatch.MethodPatchType.PATCH_INSTRUCTIONS);
                    mp.addInsnPatch(new BytecodePatch.InsnPatch(
                            BytecodePatch.InsnPatch.PatchOp.DELETE, d.getInstructionIndex(), d.getOldValue()));
                    break;
                }
                case MODIFIED: {
                    BytecodePatch.MethodPatch mp = getOrCreateMethodPatch(cp, d.getMethodName(), d.getMethodDesc(),
                            BytecodePatch.MethodPatch.MethodPatchType.PATCH_INSTRUCTIONS);
                    mp.addInsnPatch(new BytecodePatch.InsnPatch(
                            BytecodePatch.InsnPatch.PatchOp.REPLACE, d.getInstructionIndex(), d.getNewValue()));
                    break;
                }
            }
        }

        for (BytecodePatch.ClassPatch cp : classPatchMap.values()) {
            patch.addClassPatch(cp);
        }

        return patch;
    }

    private BytecodePatch.MethodPatch getOrCreateMethodPatch(BytecodePatch.ClassPatch cp,
                                                              MethodNode mn,
                                                              BytecodePatch.MethodPatch.MethodPatchType type) {
        return getOrCreateMethodPatch(cp, mn.name, mn.desc, type);
    }

    private BytecodePatch.MethodPatch getOrCreateMethodPatch(BytecodePatch.ClassPatch cp,
                                                              String name, String desc,
                                                              BytecodePatch.MethodPatch.MethodPatchType type) {
        for (BytecodePatch.MethodPatch existing : cp.getMethodPatches()) {
            if (existing.getMethodName().equals(name) && existing.getMethodDesc().equals(desc)) {
                return existing;
            }
        }
        BytecodePatch.MethodPatch mp = new BytecodePatch.MethodPatch(name, desc, type);
        cp.addMethodPatch(mp);
        return mp;
    }

    private FieldNode findField(Map<String, ClassNode> classes, String className, String name, String desc) {
        ClassNode cn = classes.get(className);
        if (cn == null || cn.fields == null) return null;
        for (FieldNode fn : cn.fields) {
            if (fn.name.equals(name) && fn.desc.equals(desc)) return fn;
        }
        return null;
    }

    private MethodNode findMethod(Map<String, ClassNode> classes, String className, String name, String desc) {
        ClassNode cn = classes.get(className);
        if (cn == null || cn.methods == null) return null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) return mn;
        }
        return null;
    }

    private ClassNode buildStubClass(String className, MethodNode mn, Map<String, ClassNode> classes) {
        ClassNode orig = classes.get(className);
        if (orig == null) return null;
        ClassNode stub = new ClassNode();
        stub.name = orig.name;
        stub.superName = orig.superName;
        stub.version = orig.version;
        stub.access = orig.access;
        stub.interfaces = orig.interfaces;
        stub.methods = new ArrayList<>();
        stub.methods.add(mn);
        stub.fields = new ArrayList<>();
        return stub;
    }
}
