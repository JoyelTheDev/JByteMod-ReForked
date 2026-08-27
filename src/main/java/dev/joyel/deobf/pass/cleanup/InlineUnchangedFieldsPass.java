package dev.joyel.deobf.pass.cleanup;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class InlineUnchangedFieldsPass extends DeobfPass {

    public InlineUnchangedFieldsPass() {
        super(PassCategory.CLEANUP,
                "Inline unchanged fields",
                "Replaces reads of static/instance fields that are never written with their default value. " +
                "Useful for ZKM constant-field obfuscation.",
                PassTag.BETTER_DECOMPILE, PassTag.BETTER_DEOBFUSCATE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        Set<String> writtenFields = new HashSet<>();
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode ain : mn.instructions) {
                    if (ain.getOpcode() == PUTFIELD || ain.getOpcode() == PUTSTATIC) {
                        FieldInsnNode fin = (FieldInsnNode) ain;
                        writtenFields.add(fin.owner + "." + fin.name + fin.desc);
                    }
                }
            }
        }

        int inlined = 0;
        for (ClassNode cn : classes.values()) {
            if ((cn.access & ACC_ENUM) != 0) continue;
            for (FieldNode fn : cn.fields) {
                String key = cn.name + "." + fn.name + fn.desc;
                if (writtenFields.contains(key)) continue;
                for (ClassNode target : classes.values()) {
                    for (MethodNode mn : target.methods) {
                        for (AbstractInsnNode ain : mn.instructions.toArray()) {
                            if (ain.getOpcode() == GETSTATIC || ain.getOpcode() == GETFIELD) {
                                FieldInsnNode fin = (FieldInsnNode) ain;
                                if (fin.owner.equals(cn.name) && fin.name.equals(fn.name) && fin.desc.equals(fn.desc)) {
                                    mn.instructions.set(ain, makeDefaultPush(Type.getType(fn.desc)));
                                    inlined++;
                                }
                            }
                        }
                    }
                }
            }
        }
        log("Inlined " + inlined + " never-written field read(s).");
        return inlined > 0;
    }

    private AbstractInsnNode makeDefaultPush(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: case Type.BYTE: case Type.CHAR:
            case Type.SHORT: case Type.INT:
                return new InsnNode(ICONST_0);
            case Type.LONG:
                return new InsnNode(LCONST_0);
            case Type.FLOAT:
                return new InsnNode(FCONST_0);
            case Type.DOUBLE:
                return new InsnNode(DCONST_0);
            default:
                return new InsnNode(ACONST_NULL);
        }
    }
}
