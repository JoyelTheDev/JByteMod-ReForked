package dev.joyel.ui.script;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public class GroovyEngine implements JbmScriptEngine {

    private final Binding binding;
    private final GroovyShell shell;

    public GroovyEngine() {
        this.binding = new Binding();
        this.shell = new GroovyShell(Thread.currentThread().getContextClassLoader(), binding);
    }

    @Override
    public String name() {
        return "Groovy";
    }

    @Override
    public void setVariable(String key, Object value) {
        binding.setVariable(key, value);
    }

    @Override
    public Object eval(String source) throws Exception {
        return shell.evaluate(source);
    }

    @Override
    public void bindContext(Map<String, ClassNode> classes, Object jbm) {
        JbmScriptEngine.super.bindContext(classes, jbm);
        binding.setVariable("out", System.out);
    }
}