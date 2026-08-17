package dev.joyel.ui.metrics;

import org.objectweb.asm.tree.*;
import java.util.*;

public final class BytecodeMetrics {

    private BytecodeMetrics() {}

    public static int instructionCount(MethodNode mn) {
        int count = 0;
        for (AbstractInsnNode ain : mn.instructions) {
            if (ain.getOpcode() >= 0) count++;
        }
        return count;
    }

    public static int cyclomaticComplexity(MethodNode mn) {
        int edges = 0;
        int nodes = 0;
        int components = 1;

        for (AbstractInsnNode ain : mn.instructions) {
            if (ain.getOpcode() < 0) continue;
            nodes++;
            int type = ain.getType();
            if (type == AbstractInsnNode.JUMP_INSN) {
                edges += 2;
            } else if (type == AbstractInsnNode.TABLESWITCH_INSN) {
                TableSwitchInsnNode ts = (TableSwitchInsnNode) ain;
                edges += ts.labels.size() + 1;
            } else if (type == AbstractInsnNode.LOOKUPSWITCH_INSN) {
                LookupSwitchInsnNode ls = (LookupSwitchInsnNode) ain;
                edges += ls.labels.size() + 1;
            } else {
                edges++;
            }
        }

        if (mn.tryCatchBlocks != null) {
            components += mn.tryCatchBlocks.size();
        }

        int cc = edges - nodes + 2 * components;
        return Math.max(1, cc);
    }

    public static int exceptionHandlerCount(MethodNode mn) {
        return mn.tryCatchBlocks == null ? 0 : mn.tryCatchBlocks.size();
    }

    public static int localVariableCount(MethodNode mn) {
        return mn.localVariables == null ? 0 : mn.localVariables.size();
    }

    public static int maxStack(MethodNode mn) {
        return mn.maxStack;
    }

    public static int maxLocals(MethodNode mn) {
        return mn.maxLocals;
    }

    public static int ldcStringCount(MethodNode mn) {
        int count = 0;
        for (AbstractInsnNode ain : mn.instructions) {
            if (ain.getType() == AbstractInsnNode.LDC_INSN && ((LdcInsnNode) ain).cst instanceof String) {
                count++;
            }
        }
        return count;
    }

    public static int branchCount(MethodNode mn) {
        int count = 0;
        for (AbstractInsnNode ain : mn.instructions) {
            int type = ain.getType();
            if (type == AbstractInsnNode.JUMP_INSN) count++;
            else if (type == AbstractInsnNode.TABLESWITCH_INSN) count++;
            else if (type == AbstractInsnNode.LOOKUPSWITCH_INSN) count++;
        }
        return count;
    }

    public static int obfuscationScore(MethodNode mn) {
        int score = 0;
        int cc = cyclomaticComplexity(mn);
        int instr = instructionCount(mn);
        boolean hasDebug = mn.localVariables != null && !mn.localVariables.isEmpty();

        if (cc > 20) score += 30;
        else if (cc > 10) score += 15;

        if (instr > 500) score += 25;
        else if (instr > 200) score += 10;

        if (!hasDebug && instr > 20) score += 20;

        if (looksObfuscatedName(mn.name)) score += 15;

        for (AbstractInsnNode ain : mn.instructions) {
            if (ain.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN) {
                score += 5;
                break;
            }
        }

        return Math.min(100, score);
    }

    public static int classObfuscationScore(ClassNode cn) {
        if (cn.methods.isEmpty()) return 0;
        int total = 0;
        for (MethodNode mn : cn.methods) {
            total += obfuscationScore(mn);
        }
        return total / cn.methods.size();
    }

    private static boolean looksObfuscatedName(String name) {
        if (name.startsWith("<")) return false;
        if (name.length() <= 2) return true;
        long nonAlpha = name.chars().filter(c -> !Character.isLetterOrDigit(c) && c != '_' && c != '$').count();
        return nonAlpha > 0 || name.chars().allMatch(c -> c < 'a' || c > 'z' + 26);
    }

    public static MethodMetrics forMethod(MethodNode mn) {
        return new MethodMetrics(
            instructionCount(mn),
            cyclomaticComplexity(mn),
            exceptionHandlerCount(mn),
            localVariableCount(mn),
            maxStack(mn),
            maxLocals(mn),
            branchCount(mn),
            ldcStringCount(mn),
            obfuscationScore(mn)
        );
    }

    public static ClassMetrics forClass(ClassNode cn) {
        int totalInstr = 0;
        int totalCC = 0;
        int maxCC = 0;
        int totalHandlers = 0;
        for (MethodNode mn : cn.methods) {
            int instr = instructionCount(mn);
            int cc = cyclomaticComplexity(mn);
            totalInstr += instr;
            totalCC += cc;
            if (cc > maxCC) maxCC = cc;
            totalHandlers += exceptionHandlerCount(mn);
        }
        int methodCount = cn.methods.size();
        int avgCC = methodCount == 0 ? 0 : totalCC / methodCount;
        int fieldCount = cn.fields == null ? 0 : cn.fields.size();
        int obfScore = classObfuscationScore(cn);
        return new ClassMetrics(methodCount, fieldCount, totalInstr, avgCC, maxCC, totalHandlers, obfScore);
    }

    public static final class MethodMetrics {
        private final int instructionCount;
        private final int cyclomaticComplexity;
        private final int exceptionHandlers;
        private final int localVariables;
        private final int maxStack;
        private final int maxLocals;
        private final int branchCount;
        private final int ldcStrings;
        private final int obfuscationScore;

        public MethodMetrics(int instructionCount, int cyclomaticComplexity, int exceptionHandlers,
                             int localVariables, int maxStack, int maxLocals,
                             int branchCount, int ldcStrings, int obfuscationScore) {
            this.instructionCount = instructionCount;
            this.cyclomaticComplexity = cyclomaticComplexity;
            this.exceptionHandlers = exceptionHandlers;
            this.localVariables = localVariables;
            this.maxStack = maxStack;
            this.maxLocals = maxLocals;
            this.branchCount = branchCount;
            this.ldcStrings = ldcStrings;
            this.obfuscationScore = obfuscationScore;
        }

        public int instructionCount() { return instructionCount; }
        public int cyclomaticComplexity() { return cyclomaticComplexity; }
        public int exceptionHandlers() { return exceptionHandlers; }
        public int localVariables() { return localVariables; }
        public int maxStack() { return maxStack; }
        public int maxLocals() { return maxLocals; }
        public int branchCount() { return branchCount; }
        public int ldcStrings() { return ldcStrings; }
        public int obfuscationScore() { return obfuscationScore; }
    }

    public static final class ClassMetrics {
        private final int methodCount;
        private final int fieldCount;
        private final int totalInstructions;
        private final int avgCyclomaticComplexity;
        private final int maxCyclomaticComplexity;
        private final int totalExceptionHandlers;
        private final int obfuscationScore;

        public ClassMetrics(int methodCount, int fieldCount, int totalInstructions,
                            int avgCyclomaticComplexity, int maxCyclomaticComplexity,
                            int totalExceptionHandlers, int obfuscationScore) {
            this.methodCount = methodCount;
            this.fieldCount = fieldCount;
            this.totalInstructions = totalInstructions;
            this.avgCyclomaticComplexity = avgCyclomaticComplexity;
            this.maxCyclomaticComplexity = maxCyclomaticComplexity;
            this.totalExceptionHandlers = totalExceptionHandlers;
            this.obfuscationScore = obfuscationScore;
        }

        public int methodCount() { return methodCount; }
        public int fieldCount() { return fieldCount; }
        public int totalInstructions() { return totalInstructions; }
        public int avgCyclomaticComplexity() { return avgCyclomaticComplexity; }
        public int maxCyclomaticComplexity() { return maxCyclomaticComplexity; }
        public int totalExceptionHandlers() { return totalExceptionHandlers; }
        public int obfuscationScore() { return obfuscationScore; }
    }
}