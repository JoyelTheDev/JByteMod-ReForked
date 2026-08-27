package dev.joyel.deobf.pass.generic;

import dev.joyel.deobf.analysis.ConstantFrames;
import dev.joyel.deobf.analysis.ConstantValue;
import dev.joyel.deobf.analysis.InsnListUtil;
import dev.joyel.deobf.api.DeobfPass;
import dev.joyel.deobf.api.PassCategory;
import dev.joyel.deobf.api.PassTag;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.Map;

public final class KnownConditionalJumpsPass extends DeobfPass {

    public KnownConditionalJumpsPass() {
        super(PassCategory.GENERIC,
                "Remove obvious flow obfuscation",
                "Removes conditional jumps whose outcome is statically known (constant stack value). " +
                "Effective against Smoke, Superblaubeere27, ZKM, and similar flow obfuscators.",
                PassTag.BETTER_DECOMPILE, PassTag.BETTER_DEOBFUSCATE);
    }

    private int predictedJumps;
    private int predictedSwitches;

    @Override
    public boolean execute(Map<String, ClassNode> classes, boolean verbose) {
        predictedJumps = 0;
        predictedSwitches = 0;
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                processMethod(cn, mn, verbose);
            }
        }
        log("Removed " + predictedJumps + " unconditional jumps and " + predictedSwitches + " switches.");
        return predictedJumps > 0 || predictedSwitches > 0;
    }

    private void processMethod(ClassNode cn, MethodNode mn, boolean verbose) {
        InsnList rewritten = new InsnList();
        Map<LabelNode, LabelNode> labels = InsnListUtil.cloneLabels(mn.instructions);

        ConstantFrames.forEach(cn, mn, (ain, frame) -> {
            if (frame == null) { rewritten.add(ain.clone(labels)); return; }

            if (ain.getType() == AbstractInsnNode.JUMP_INSN) {
                try {
                    int result = predictJump(frame, ain.getOpcode());
                    if (result != 0) {
                        int popCount = Math.abs(result);
                        if (popCount == 2) {
                            rewritten.add(new InsnNode(POP2));
                        } else if (popCount == 1) {
                            rewritten.add(new InsnNode(POP));
                        }
                        if (result > 0) {
                            rewritten.add(new JumpInsnNode(GOTO, labels.get(((JumpInsnNode) ain).label)));
                        }
                        predictedJumps++;
                        return;
                    }
                } catch (Exception e) {
                    if (verbose) err("Stack prediction error in " + cn.name + "." + mn.name + ": " + e.getMessage());
                }
            } else if (ain.getOpcode() == LOOKUPSWITCH && frame.getStackSize() > 0) {
                ConstantValue top = frame.getStack(frame.getStackSize() - 1);
                if (top.isKnown() && top.isInteger()) {
                    LookupSwitchInsnNode lsin = (LookupSwitchInsnNode) ain;
                    int key = top.getAsInteger();
                    int idx = lsin.keys.indexOf(key);
                    rewritten.add(new InsnNode(POP));
                    LabelNode target = idx == -1 ? lsin.dflt : lsin.labels.get(idx);
                    rewritten.add(new JumpInsnNode(GOTO, labels.get(target)));
                    predictedSwitches++;
                    return;
                }
            } else if (ain.getOpcode() == TABLESWITCH && frame.getStackSize() > 0) {
                ConstantValue top = frame.getStack(frame.getStackSize() - 1);
                if (top.isKnown() && top.isInteger()) {
                    TableSwitchInsnNode tsin = (TableSwitchInsnNode) ain;
                    int key = top.getAsInteger();
                    int idx = key - tsin.min;
                    boolean useDflt = idx < 0 || idx >= tsin.labels.size();
                    rewritten.add(new InsnNode(POP));
                    LabelNode target = useDflt ? tsin.dflt : tsin.labels.get(idx);
                    rewritten.add(new JumpInsnNode(GOTO, labels.get(target)));
                    predictedSwitches++;
                    return;
                }
            }
            rewritten.add(ain.clone(labels));
        });

        if (rewritten.size() > 0) {
            InsnListUtil.updateInstructions(mn, labels, rewritten);
            InsnListUtil.removeDeadCode(cn, mn);
        }
    }

    private int predictJump(Frame<ConstantValue> frame, int op) {
        if (frame.getStackSize() == 0) return 0;
        ConstantValue top = frame.getStack(frame.getStackSize() - 1);
        if (!top.isKnown()) return 0;
        Object tv = top.getValue();

        switch (op) {
            case IFEQ:    return toInt(tv) == 0 ? 1 : -1;
            case IFNE:    return toInt(tv) != 0 ? 1 : -1;
            case IFGT:    return toInt(tv) > 0 ? 1 : -1;
            case IFGE:    return toInt(tv) >= 0 ? 1 : -1;
            case IFLT:    return toInt(tv) < 0 ? 1 : -1;
            case IFLE:    return toInt(tv) <= 0 ? 1 : -1;
            case IFNULL:    return top.isNull() ? 1 : -1;
            case IFNONNULL: return top.isNull() ? -1 : 1;
        }

        if (frame.getStackSize() >= 2) {
            ConstantValue low = frame.getStack(frame.getStackSize() - 2);
            if (!low.isKnown()) return 0;
            Object lv = low.getValue();
            switch (op) {
                case IF_ICMPEQ: return tv.equals(lv) ? 2 : -2;
                case IF_ICMPNE: return tv.equals(lv) ? -2 : 2;
                case IF_ICMPLT: return toInt(lv) < toInt(tv) ? 2 : -2;
                case IF_ICMPGE: return toInt(lv) >= toInt(tv) ? 2 : -2;
                case IF_ICMPGT: return toInt(lv) > toInt(tv) ? 2 : -2;
                case IF_ICMPLE: return toInt(lv) <= toInt(tv) ? 2 : -2;
                case IF_ACMPEQ: return top.equals(low) ? 2 : -2;
                case IF_ACMPNE: return !top.equals(low) ? 2 : -2;
            }
        }
        return 0;
    }

    private static int toInt(Object v) {
        if (v instanceof Character) return (int) (char) (Character) v;
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        return ((Number) v).intValue();
    }
}
