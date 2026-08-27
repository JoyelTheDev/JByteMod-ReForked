package dev.joyel.deobf.analysis;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.function.BiConsumer;

public final class ConstantFrames {

    private ConstantFrames() {}

    @SuppressWarnings("unchecked")
    public static Frame<ConstantValue>[] analyze(ClassNode cn, MethodNode mn) {
        boolean isStatic = (mn.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0;
        ConstantTracker tracker = new ConstantTracker(isStatic, mn.maxLocals, mn.desc, new Object[0]);
        Analyzer<ConstantValue> analyzer = new Analyzer<>(tracker);
        try {
            analyzer.analyze(cn.name, mn);
            return analyzer.getFrames();
        } catch (AnalyzerException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void forEach(ClassNode cn, MethodNode mn,
                               BiConsumer<AbstractInsnNode, Frame<ConstantValue>> consumer) {
        Frame<ConstantValue>[] frames = analyze(cn, mn);
        if (frames == null) return;
        AbstractInsnNode[] insns = mn.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            consumer.accept(insns[i], frames[i]);
        }
    }
}
