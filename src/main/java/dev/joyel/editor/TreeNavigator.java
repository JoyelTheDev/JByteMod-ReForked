package dev.joyel.editor;

import me.grax.jbytemod.ui.tree.SortedTreeNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.function.Predicate;

public final class TreeNavigator {

    private TreeNavigator() {
    }

    public static void selectClass(JTree tree, ClassNode classNode) {
        if (tree == null || classNode == null) {
            return;
        }
        SortedTreeNode node = find(root(tree), n -> n.getClassNode() == classNode && n.getMethodNode() == null);
        select(tree, node);
    }

    public static void selectMethod(JTree tree, ClassNode classNode, MethodNode methodNode) {
        if (tree == null || classNode == null || methodNode == null) {
            return;
        }
        SortedTreeNode node = find(root(tree), n -> n.getClassNode() == classNode && n.getMethodNode() == methodNode);
        select(tree, node);
    }

    public static void selectPackage(JTree tree, String[] parts, int depth) {
        if (tree == null) {
            return;
        }
        SortedTreeNode node = root(tree);
        for (int i = 0; i <= depth && node != null; i++) {
            node = child(node, parts[i]);
        }
        if (node != null) {
            TreePath path = new TreePath(node.getPath());
            tree.setSelectionPath(path);
            tree.expandPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private static SortedTreeNode root(JTree tree) {
        Object root = tree.getModel().getRoot();
        return root instanceof SortedTreeNode ? (SortedTreeNode) root : null;
    }

    private static SortedTreeNode child(SortedTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            TreeNode child = parent.getChildAt(i);
            if (child instanceof SortedTreeNode) {
                SortedTreeNode candidate = (SortedTreeNode) child;
                if (candidate.getClassNode() == null && !candidate.isResource() && name.equals(candidate.toString())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static SortedTreeNode find(SortedTreeNode node, Predicate<SortedTreeNode> test) {
        if (node == null) {
            return null;
        }
        if (test.test(node)) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode child = node.getChildAt(i);
            if (child instanceof SortedTreeNode) {
                SortedTreeNode found = find((SortedTreeNode) child, test);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void select(JTree tree, SortedTreeNode node) {
        if (node == null) {
            return;
        }
        TreePath path = new TreePath(node.getPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }
}
