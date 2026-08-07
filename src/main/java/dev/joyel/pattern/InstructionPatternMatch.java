package dev.joyel.pattern;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

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
        this.instructions = List.copyOf(instructions);
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

    public AbstractInsnNode getLastInstruction() {
        return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
    }

    public AbstractInsnNode getNavigationInstruction() {
        return instructions.stream()
                .filter(i -> i.getOpcode() >= 0)
                .findFirst()
                .orElse(getFirstInstruction());
    }

    public String getMethodDisplayName() {
        return ownerClass.name + "#" + method.name + method.desc;
    }
}
