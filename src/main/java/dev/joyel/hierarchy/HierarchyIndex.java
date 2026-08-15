package dev.joyel.hierarchy;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HierarchyIndex {

    private final Map<String, ClassHierarchyNode> classNodes = new LinkedHashMap<String, ClassHierarchyNode>();
    private final Map<MethodKey, MethodHierarchyGroup> methodGroups = new LinkedHashMap<MethodKey, MethodHierarchyGroup>();

    private HierarchyIndex() {}

    public static HierarchyIndex build(Map<String, ClassNode> classes) {
        HierarchyIndex index = new HierarchyIndex();
        index.buildClassNodes(classes);
        index.buildClassHierarchy();
        index.buildMethodGroups();
        return index;
    }

    private void buildClassNodes(Map<String, ClassNode> classes) {
        for (ClassNode cn : classes.values()) {
            classNodes.put(cn.name, new ClassHierarchyNode(cn));
        }
    }

    private void buildClassHierarchy() {
        Set<String> visited  = new HashSet<String>();
        Set<String> visiting = new HashSet<String>();

        for (String name : classNodes.keySet()) {
            buildNodeHierarchy(name, visited, visiting);
        }

        for (ClassHierarchyNode node : classNodes.values()) {
            for (ClassHierarchyNode ancestor : node.getExtending()) {
                ancestor.addInheritor(node);
            }
        }
    }

    private void buildNodeHierarchy(String name, Set<String> visited, Set<String> visiting) {
        if (visited.contains(name) || !classNodes.containsKey(name)) return;
        if (!visiting.add(name)) return;

        ClassHierarchyNode node = classNodes.get(name);
        ClassNode cn = node.getClassNode();

        if (cn.superName != null && !cn.superName.equals("java/lang/Object")) {
            buildNodeHierarchy(cn.superName, visited, visiting);
            ClassHierarchyNode superNode = classNodes.get(cn.superName);
            if (superNode != null) {
                node.setSuperClass(superNode);
                node.addExtending(superNode);
                for (ClassHierarchyNode transitiveAncestor : superNode.getExtending()) {
                    node.addExtending(transitiveAncestor);
                }
                for (ClassHierarchyNode iface : superNode.getInterfaces()) {
                    node.addInterface(iface);
                }
            }
        }

        if (cn.interfaces != null) {
            for (String ifaceName : cn.interfaces) {
                buildNodeHierarchy(ifaceName, visited, visiting);
                ClassHierarchyNode ifaceNode = classNodes.get(ifaceName);
                if (ifaceNode != null) {
                    node.addInterface(ifaceNode);
                    node.addExtending(ifaceNode);
                    for (ClassHierarchyNode transitiveIface : ifaceNode.getExtending()) {
                        node.addExtending(transitiveIface);
                    }
                }
            }
        }

        visiting.remove(name);
        visited.add(name);
    }

    private void buildMethodGroups() {
        Map<MethodKey, MethodHierarchyGroup> rootGroups = new HashMap<MethodKey, MethodHierarchyGroup>();

        for (ClassHierarchyNode node : classNodes.values()) {
            ClassNode cn = node.getClassNode();
            for (MethodNode mn : cn.methods) {
                if (!canParticipateInOverride(mn)) continue;

                MethodKey key = new MethodKey(cn.name, mn.name, mn.desc);
                MethodHierarchyGroup group = findOrCreateGroup(node, mn, rootGroups);
                group.add(cn, mn);
                methodGroups.put(key, group);
            }
        }
    }

    private MethodHierarchyGroup findOrCreateGroup(ClassHierarchyNode owner,
                                                    MethodNode mn,
                                                    Map<MethodKey, MethodHierarchyGroup> rootGroups) {
        for (ClassHierarchyNode ancestor : owner.getExtending()) {
            ClassNode ancestorCn = ancestor.getClassNode();
            MethodNode superMn = findDeclaredMethod(ancestorCn, mn.name, mn.desc);
            if (superMn != null && canOverride(mn, superMn)) {
                MethodKey ancestorKey = new MethodKey(ancestorCn.name, mn.name, mn.desc);
                MethodHierarchyGroup existing = rootGroups.get(ancestorKey);
                if (existing != null) return existing;
            }
        }
        MethodKey rootKey = new MethodKey(owner.getClassNode().name, mn.name, mn.desc);
        MethodHierarchyGroup group = new MethodHierarchyGroup(mn.name, mn.desc);
        rootGroups.put(rootKey, group);
        return group;
    }

    private static MethodNode findDeclaredMethod(ClassNode cn, String name, String desc) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) return mn;
        }
        return null;
    }

    private static boolean canParticipateInOverride(MethodNode mn) {
        boolean isInit    = mn.name.equals("<init>") || mn.name.equals("<clinit>");
        boolean isStatic  = (mn.access & Opcodes.ACC_STATIC)  != 0;
        boolean isPrivate = (mn.access & Opcodes.ACC_PRIVATE) != 0;
        return !isInit && !isStatic && !isPrivate;
    }

    private static boolean canOverride(MethodNode method, MethodNode superMethod) {
        if (!canParticipateInOverride(superMethod)) return false;
        if ((superMethod.access & Opcodes.ACC_FINAL) != 0) return false;
        int access = superMethod.access;
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
    }

    public ClassHierarchyNode getClassNode(String internalName) {
        return classNodes.get(internalName);
    }

    public MethodHierarchyGroup getMethodGroup(String owner, String name, String desc) {
        return methodGroups.get(new MethodKey(owner, name, desc));
    }

    public Collection<ClassHierarchyNode> allClassNodes() {
        return classNodes.values();
    }

    public Collection<MethodHierarchyGroup> allMethodGroups() {
        return methodGroups.values();
    }

    public List<ClassHierarchyNode> getSuperChain(String internalName) {
        List<ClassHierarchyNode> chain = new ArrayList<ClassHierarchyNode>();
        ClassHierarchyNode node = classNodes.get(internalName);
        Set<String> seen = new HashSet<String>();
        while (node != null && seen.add(node.getClassNode().name)) {
            chain.add(node);
            node = node.getSuperClass();
        }
        return chain;
    }

    public List<ClassHierarchyNode> getInheritorChain(String internalName) {
        List<ClassHierarchyNode> result = new ArrayList<ClassHierarchyNode>();
        ClassHierarchyNode root = classNodes.get(internalName);
        if (root == null) return result;
        ArrayDeque<ClassHierarchyNode> queue = new ArrayDeque<ClassHierarchyNode>();
        Set<String> seen = new HashSet<String>();
        queue.add(root);
        while (!queue.isEmpty()) {
            ClassHierarchyNode current = queue.removeFirst();
            if (!seen.add(current.getClassNode().name)) continue;
            result.add(current);
            for (ClassHierarchyNode sub : current.getInheritors()) {
                queue.addLast(sub);
            }
        }
        return result;
    }

    static final class MethodKey {
        final String owner;
        final String name;
        final String desc;

        MethodKey(String owner, String name, String desc) {
            this.owner = owner;
            this.name  = name;
            this.desc  = desc;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MethodKey)) return false;
            MethodKey k = (MethodKey) o;
            return owner.equals(k.owner) && name.equals(k.name) && desc.equals(k.desc);
        }

        @Override
        public int hashCode() {
            int h = owner.hashCode();
            h = 31 * h + name.hashCode();
            h = 31 * h + desc.hashCode();
            return h;
        }
    }
}
