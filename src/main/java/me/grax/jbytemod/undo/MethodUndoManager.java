package me.grax.jbytemod.undo;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.IdentityHashMap;

public class MethodUndoManager {

    private static final int MAX_HISTORY = 20;

    private static final IdentityHashMap<Object, MethodUndoManager> REGISTRY = new IdentityHashMap<>();

    public static MethodUndoManager get(Object methodNode) {
        return REGISTRY.computeIfAbsent(methodNode, k -> new MethodUndoManager());
    }

    public static void evict(Object methodNode) {
        REGISTRY.remove(methodNode);
    }

    private final ArrayDeque<InsnList> undoStack = new ArrayDeque<>();
    private final ArrayDeque<InsnList> redoStack = new ArrayDeque<>();

    private MethodUndoManager() {}

    public void snapshot(InsnList current) {
        redoStack.clear();
        if (undoStack.size() >= MAX_HISTORY) {
            undoStack.pollLast();
        }
        undoStack.push(deepCopy(current));
    }

    public InsnList undo(InsnList current) {
        if (undoStack.isEmpty()) return null;
        if (redoStack.size() >= MAX_HISTORY) redoStack.pollLast();
        redoStack.push(deepCopy(current));
        return undoStack.pop();
    }

    public InsnList redo(InsnList current) {
        if (redoStack.isEmpty()) return null;
        if (undoStack.size() >= MAX_HISTORY) undoStack.pollLast();
        undoStack.push(deepCopy(current));
        return redoStack.pop();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    private static InsnList deepCopy(InsnList src) {
        HashMap<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode ain : src) {
            if (ain instanceof LabelNode) {
                labelMap.put((LabelNode) ain, new LabelNode());
            }
        }
        InsnList copy = new InsnList();
        for (AbstractInsnNode ain : src) {
            if (ain instanceof LabelNode) {
                copy.add(labelMap.get(ain));
            } else {
                copy.add(ain.clone(labelMap));
            }
        }
        return copy;
    }
}
