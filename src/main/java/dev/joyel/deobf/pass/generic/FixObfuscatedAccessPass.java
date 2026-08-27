package dev.joyel.deobf.pass.generic;

import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public final class FixObfuscatedAccessPass extends DeobfPass {

    public FixObfuscatedAccessPass() {
        super(PassCategory.GENERIC,
                "Fix obfuscated access flags",
                "Removes synthetic, bridge, and deprecated flags that obfuscators add to confuse decompilers.",
                PassTag.BETTER_DECOMPILE);
    }

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        int methodFixed = 0, fieldFixed = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if (shouldFix(mn)) {
                    mn.access = removeFlags(mn.access, ACC_SYNTHETIC, ACC_BRIDGE, ACC_DEPRECATED);
                    methodFixed++;
                }
            }
            for (FieldNode fn : cn.fields) {
                if (shouldFix(fn)) {
                    fn.access = removeFlags(fn.access, ACC_SYNTHETIC, ACC_BRIDGE, ACC_DEPRECATED);
                    fieldFixed++;
                }
            }
        }
        log("Cleaned " + methodFixed + " methods and " + fieldFixed + " fields.");
        return methodFixed > 0 || fieldFixed > 0;
    }

    private boolean shouldFix(MethodNode mn) {
        boolean hasSyntheticOrBridge = (mn.access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0;
        if (!hasSyntheticOrBridge) return false;
        boolean isLambdaOrAccess = (mn.access & ACC_STATIC) != 0
                && (mn.name.startsWith("access$") || mn.name.startsWith("lambda$"));
        return !isLambdaOrAccess;
    }

    private boolean shouldFix(FieldNode fn) {
        boolean hasSyntheticOrBridge = (fn.access & (ACC_SYNTHETIC | ACC_BRIDGE)) != 0;
        if (!hasSyntheticOrBridge) return false;
        boolean isCapturedField = (fn.access & ACC_FINAL) != 0
                && (fn.name.startsWith("val$") || fn.name.startsWith("this$"));
        return !isCapturedField;
    }

    private static int removeFlags(int access, int... flags) {
        for (int f : flags) access &= ~f;
        return access;
    }
}
