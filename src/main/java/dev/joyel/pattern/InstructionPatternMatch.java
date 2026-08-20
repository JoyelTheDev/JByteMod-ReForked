package dev.joyel.pattern;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;
import java.util.List;

public final class InstructionPatternMatch {
    private final ClassNode ownerClass;
    private final MethodNode method;
    private final List<AbstractInsnNode> instructions;
    private final String formattedInstructions;

    public InstructionPatternMatch(ClassNode ownerClass, MethodNode method,
                                   List<AbstractInsnNode> instructions, String formattedInstructions) {
        this.ownerClass = ownerClass;
        this.method = method;
        this.instructions = Collections.unmodifiableList(instructions);
        this.formattedInstructions = formattedInstructions;
    }

    public ClassNode getOwnerClass() {
        return ownerClass;
    }

    public MethodNode getMethod() {
        return method;
    }

    public List<AbstractInsnNode> getInstructions() {
        return instructions;
    }

    public String getFormattedInstructions() {
        return formattedInstructions;
    }

    public AbstractInsnNode getFirstInstruction() {
        return instructions.isEmpty() ? null : instructions.get(0);
    }

    public AbstractInsnNode getNavigationInstruction() {
        for (AbstractInsnNode instruction : instructions) {
            if (instruction.getOpcode() >= 0) {
                return instruction;
            }
        }
        return getFirstInstruction();
    }

    public String getMethodDisplayName() {
        return ownerClass.name + "#" + method.name + method.desc;
    }
}