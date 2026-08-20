package dev.joyel.ui.metrics;

import org.objectweb.asm.tree.*;
import java.util.*;

public final class BytecodeMetrics {
    private BytecodeMetrics() {}

    public static int instructionCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                count++;
            }
        }
        return count;
    }

    public static int cyclomaticComplexity(MethodNode method) {
        int edges = 0;
        int nodes = 0;
        int components = 1;

        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() < 0) {
                continue;
            }
            nodes++;
            int type = instruction.getType();
            if (type == AbstractInsnNode.JUMP_INSN) {
                edges += 2;
            } else if (type == AbstractInsnNode.TABLESWITCH_INSN) {
                TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode) instruction;
                edges += tableSwitch.labels.size() + 1;
            } else if (type == AbstractInsnNode.LOOKUPSWITCH_INSN) {
                LookupSwitchInsnNode lookupSwitch = (LookupSwitchInsnNode) instruction;
                edges += lookupSwitch.labels.size() + 1;
            } else {
                edges++;
            }
        }

        if (method.tryCatchBlocks != null) {
            components += method.tryCatchBlocks.size();
        }

        int complexity = edges - nodes + 2 * components;
        return Math.max(1, complexity);
    }

    public static int exceptionHandlerCount(MethodNode method) {
        return method.tryCatchBlocks == null ? 0 : method.tryCatchBlocks.size();
    }

    public static int localVariableCount(MethodNode method) {
        return method.localVariables == null ? 0 : method.localVariables.size();
    }

    public static int maxStack(MethodNode method) {
        return method.maxStack;
    }

    public static int maxLocals(MethodNode method) {
        return method.maxLocals;
    }

    public static int ldcStringCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getType() == AbstractInsnNode.LDC_INSN) {
                LdcInsnNode ldc = (LdcInsnNode) instruction;
                if (ldc.cst instanceof String) {
                    count++;
                }
            }
        }
        return count;
    }

    public static int branchCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            int type = instruction.getType();
            if (type == AbstractInsnNode.JUMP_INSN ||
                type == AbstractInsnNode.TABLESWITCH_INSN ||
                type == AbstractInsnNode.LOOKUPSWITCH_INSN) {
                count++;
            }
        }
        return count;
    }

    public static int obfuscationScore(MethodNode method) {
        int score = 0;
        int complexity = cyclomaticComplexity(method);
        int instructionCount = instructionCount(method);
        boolean hasDebug = method.localVariables != null && !method.localVariables.isEmpty();

        if (complexity > 20) {
            score += 30;
        } else if (complexity > 10) {
            score += 15;
        }

        if (instructionCount > 500) {
            score += 25;
        } else if (instructionCount > 200) {
            score += 10;
        }

        if (!hasDebug && instructionCount > 20) {
            score += 20;
        }

        if (looksObfuscatedName(method.name)) {
            score += 15;
        }

        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getType() == AbstractInsnNode.INVOKE_DYNAMIC_INSN) {
                score += 5;
                break;
            }
        }

        return Math.min(100, score);
    }

    public static int classObfuscationScore(ClassNode classNode) {
        if (classNode.methods.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (MethodNode method : classNode.methods) {
            total += obfuscationScore(method);
        }
        return total / classNode.methods.size();
    }

    private static boolean looksObfuscatedName(String name) {
        if (name.startsWith("<")) {
            return false;
        }
        if (name.length() <= 2) {
            return true;
        }
        long nonAlpha = name.chars()
                .filter(c -> !Character.isLetterOrDigit(c) && c != '_' && c != '$')
                .count();
        if (nonAlpha > 0) {
            return true;
        }
        return name.chars().allMatch(c -> c < 'a' || c > 'z' + 26);
    }

    public static MethodMetrics forMethod(MethodNode method) {
        return new MethodMetrics(
                instructionCount(method),
                cyclomaticComplexity(method),
                exceptionHandlerCount(method),
                localVariableCount(method),
                maxStack(method),
                maxLocals(method),
                branchCount(method),
                ldcStringCount(method),
                obfuscationScore(method)
        );
    }

    public static ClassMetrics forClass(ClassNode classNode) {
        int totalInstructions = 0;
        int totalComplexity = 0;
        int maxComplexity = 0;
        int totalHandlers = 0;

        for (MethodNode method : classNode.methods) {
            int instructions = instructionCount(method);
            int complexity = cyclomaticComplexity(method);
            totalInstructions += instructions;
            totalComplexity += complexity;
            if (complexity > maxComplexity) {
                maxComplexity = complexity;
            }
            totalHandlers += exceptionHandlerCount(method);
        }

        int methodCount = classNode.methods.size();
        int averageComplexity = methodCount == 0 ? 0 : totalComplexity / methodCount;
        int fieldCount = classNode.fields == null ? 0 : classNode.fields.size();
        int obfuscationScore = classObfuscationScore(classNode);

        return new ClassMetrics(
                methodCount,
                fieldCount,
                totalInstructions,
                averageComplexity,
                maxComplexity,
                totalHandlers,
                obfuscationScore
        );
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

        public int instructionCount() {
            return instructionCount;
        }

        public int cyclomaticComplexity() {
            return cyclomaticComplexity;
        }

        public int exceptionHandlers() {
            return exceptionHandlers;
        }

        public int localVariables() {
            return localVariables;
        }

        public int maxStack() {
            return maxStack;
        }

        public int maxLocals() {
            return maxLocals;
        }

        public int branchCount() {
            return branchCount;
        }

        public int ldcStrings() {
            return ldcStrings;
        }

        public int obfuscationScore() {
            return obfuscationScore;
        }
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

        public int methodCount() {
            return methodCount;
        }

        public int fieldCount() {
            return fieldCount;
        }

        public int totalInstructions() {
            return totalInstructions;
        }

        public int avgCyclomaticComplexity() {
            return avgCyclomaticComplexity;
        }

        public int maxCyclomaticComplexity() {
            return maxCyclomaticComplexity;
        }

        public int totalExceptionHandlers() {
            return totalExceptionHandlers;
        }

        public int obfuscationScore() {
            return obfuscationScore;
        }
    }
}