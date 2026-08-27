package dev.joyel.deobf.api;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.Map;

public abstract class DeobfPass implements Opcodes {

    public final String name;
    public final PassCategory category;
    public final String description;
    public final PassTag[] tags;

    protected DeobfPass(PassCategory category, String name, String description, PassTag... tags) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.tags = tags;
    }

    public abstract boolean execute(Map<String, ClassNode> classes, boolean verbose);

    public String getAuthor() { return null; }

    @Override
    public String toString() { return name; }

    protected MethodNode getMethod(ClassNode node, String methodName, String desc) {
        if (node == null) return null;
        return node.methods.stream()
                .filter(m -> m.name.equals(methodName) && m.desc.equals(desc))
                .findFirst().orElse(null);
    }

    protected MethodNode getStaticInitializer(ClassNode node) {
        return node.methods.stream()
                .filter(m -> m.name.equals("<clinit>"))
                .findFirst().orElse(null);
    }

    protected boolean removeDeadCode(ClassNode cn, MethodNode mn) {
        Analyzer<?> analyzer = new Analyzer<>(new BasicInterpreter());
        try {
            analyzer.analyze(cn.name, mn);
        } catch (AnalyzerException e) {
            return false;
        }
        Frame<?>[] frames = analyzer.getFrames();
        org.objectweb.asm.tree.AbstractInsnNode[] insns = mn.instructions.toArray();
        for (int i = 0; i < frames.length; i++) {
            org.objectweb.asm.tree.AbstractInsnNode insn = insns[i];
            if (frames[i] == null && insn.getType() != org.objectweb.asm.tree.AbstractInsnNode.LABEL) {
                mn.instructions.remove(insn);
                insns[i] = null;
            }
        }
        return true;
    }

    protected void log(String msg) {
        de.xbrowniecodez.jbytemod.Main.INSTANCE.getLogger().log("[" + name + "] " + msg);
    }

    protected void warn(String msg) {
        de.xbrowniecodez.jbytemod.Main.INSTANCE.getLogger().warn("[" + name + "] " + msg);
    }

    protected void err(String msg) {
        de.xbrowniecodez.jbytemod.Main.INSTANCE.getLogger().err("[" + name + "] " + msg);
    }
}
