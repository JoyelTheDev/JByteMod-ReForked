package dev.joyel.nbridge;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public interface NativeHost {
    Map<String, ClassNode> classes();

    Map<String, byte[]> resources();

    void goTo(ClassNode classNode, MethodNode methodNode);
}
