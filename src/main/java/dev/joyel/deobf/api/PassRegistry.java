package dev.joyel.deobf.api;

import dev.joyel.deobf.pass.analysis.RemoveMonitorsPass;
import dev.joyel.deobf.pass.analysis.RemoveTryCatchBlocksPass;
import dev.joyel.deobf.pass.cleanup.InlineMethodsPass;
import dev.joyel.deobf.pass.cleanup.InlineUnchangedFieldsPass;
import dev.joyel.deobf.pass.cleanup.RemoveAttributesPass;
import dev.joyel.deobf.pass.cleanup.RemoveUnusedVariablesPass;
import dev.joyel.deobf.pass.generic.ConvertCompareInstructionsPass;
import dev.joyel.deobf.pass.generic.FixObfuscatedAccessPass;
import dev.joyel.deobf.pass.generic.KnownConditionalJumpsPass;
import dev.joyel.deobf.pass.generic.RemoveTryCatchObfuscationPass;
import dev.joyel.deobf.pass.tools.Java8CompatibilityPass;
import dev.joyel.deobf.pass.tools.ResetMaxStackLocalsPass;
import dev.joyel.deobf.pass.zkm.ZkmFlowObfuscationPass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PassRegistry {

    private static final List<Class<? extends DeobfPass>> ALL = new ArrayList<>();

    static {
        ALL.add(InlineMethodsPass.class);
        ALL.add(InlineUnchangedFieldsPass.class);
        ALL.add(RemoveAttributesPass.class);
        ALL.add(RemoveUnusedVariablesPass.class);

        ALL.add(FixObfuscatedAccessPass.class);
        ALL.add(KnownConditionalJumpsPass.class);
        ALL.add(ConvertCompareInstructionsPass.class);
        ALL.add(RemoveTryCatchObfuscationPass.class);

        ALL.add(RemoveMonitorsPass.class);
        ALL.add(RemoveTryCatchBlocksPass.class);

        ALL.add(ZkmFlowObfuscationPass.class);

        ALL.add(Java8CompatibilityPass.class);
        ALL.add(ResetMaxStackLocalsPass.class);
    }

    public static List<Class<? extends DeobfPass>> getAll() {
        return Collections.unmodifiableList(ALL);
    }

    public static DeobfPass instantiate(Class<? extends DeobfPass> cls) {
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate pass: " + cls.getSimpleName(), e);
        }
    }

    private PassRegistry() {}
}
