package dev.joyel.ui.script;

import org.objectweb.asm.tree.ClassNode;
import java.util.Map;

public interface JbmScriptEngine {

    String name();

    void setVariable(String key, Object value);

    Object eval(String source) throws Exception;

    default void bindContext(Map<String, ClassNode> classes, Object jbm) {
        setVariable("classes", classes);
        setVariable("jbm", jbm);
        setVariable("asm", org.objectweb.asm.Opcodes.class);
    }
}