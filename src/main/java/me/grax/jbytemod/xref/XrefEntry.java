package me.grax.jbytemod.xref;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class XrefEntry {
    private final XrefKind kind;
    private final XrefAccessType access;
    private final String invocation;
    private final String whereText;
    private final ClassNode ownerClass;
    private final MethodNode ownerMethod;

    public XrefEntry(XrefKind kind, XrefAccessType access, String invocation,
                     String whereText, ClassNode ownerClass, MethodNode ownerMethod) {
        this.kind = kind;
        this.access = access;
        this.invocation = invocation;
        this.whereText = whereText;
        this.ownerClass = ownerClass;
        this.ownerMethod = ownerMethod;
    }

    public XrefKind getKind() {
        return kind;
    }

    public XrefAccessType getAccess() {
        return access;
    }

    public String getInvocation() {
        return invocation;
    }

    public String getWhereText() {
        return whereText;
    }

    public ClassNode getOwnerClass() {
        return ownerClass;
    }

    public MethodNode getOwnerMethod() {
        return ownerMethod;
    }

    public boolean hasNavigationTarget() {
        return ownerClass != null;
    }
}
