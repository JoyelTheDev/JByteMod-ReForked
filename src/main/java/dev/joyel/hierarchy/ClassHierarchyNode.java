package dev.joyel.hierarchy;

import org.objectweb.asm.tree.ClassNode;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ClassHierarchyNode {

    private final ClassNode classNode;
    private ClassHierarchyNode superClass;
    private final Set<ClassHierarchyNode> interfaces  = new LinkedHashSet<ClassHierarchyNode>();
    private final Set<ClassHierarchyNode> extending   = new LinkedHashSet<ClassHierarchyNode>();
    private final Set<ClassHierarchyNode> inheritors  = new LinkedHashSet<ClassHierarchyNode>();

    ClassHierarchyNode(ClassNode classNode) {
        this.classNode = classNode;
    }

    void setSuperClass(ClassHierarchyNode node) {
        this.superClass = node;
    }

    void addInterface(ClassHierarchyNode node) {
        interfaces.add(node);
        extending.add(node);
    }

    void addExtending(ClassHierarchyNode node) {
        extending.add(node);
    }

    void addInheritor(ClassHierarchyNode node) {
        inheritors.add(node);
    }

    public ClassNode getClassNode()                 { return classNode; }
    public ClassHierarchyNode getSuperClass()       { return superClass; }
    public Set<ClassHierarchyNode> getInterfaces()  { return interfaces; }
    public Set<ClassHierarchyNode> getExtending()   { return extending; }
    public Set<ClassHierarchyNode> getInheritors()  { return inheritors; }
}
