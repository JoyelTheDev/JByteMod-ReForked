package me.grax.jbytemod.bookmark;

import me.grax.jbytemod.utils.InstrUtils;
import me.grax.jbytemod.utils.TextUtils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class Bookmark {

    private final ClassNode classNode;
    private final MethodNode methodNode;
    private final AbstractInsnNode insnNode;
    private String label;

    public Bookmark(ClassNode classNode, MethodNode methodNode, AbstractInsnNode insnNode, String label) {
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.insnNode = insnNode;
        this.label = label;
    }

    public ClassNode getClassNode() { return classNode; }
    public MethodNode getMethodNode() { return methodNode; }
    public AbstractInsnNode getInsnNode() { return insnNode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    @Override
    public String toString() {
        String className = classNode.name.contains("/")
                ? classNode.name.substring(classNode.name.lastIndexOf('/') + 1)
                : classNode.name;
        String instr = InstrUtils.toEasyString(insnNode);
        String display = className + "." + methodNode.name + " → " + instr;
        if (label != null && !label.isEmpty()) {
            display = "[" + label + "] " + display;
        }
        return TextUtils.toHtml(TextUtils.escape(display));
    }
}
