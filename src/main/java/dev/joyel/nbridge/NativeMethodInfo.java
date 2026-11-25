package dev.joyel.nbridge;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public record NativeMethodInfo(
        ClassNode classNode,
        MethodNode methodNode,
        String shortName,
        String longName,
        List<String> libraryHints,
        List<NativeMatch> matches,
        MatchStatus status) {

    public String owner() {
        return classNode.name;
    }

    public String name() {
        return methodNode.name;
    }

    public String descriptor() {
        return methodNode.desc;
    }
}
