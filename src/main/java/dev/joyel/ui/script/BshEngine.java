package dev.joyel.ui.script;

import bsh.Interpreter;

public class BshEngine implements JbmScriptEngine {

    private final Interpreter interpreter;

    public BshEngine() {
        this.interpreter = new Interpreter();
    }

    @Override
    public String name() {
        return "BeanShell";
    }

    @Override
    public void setVariable(String key, Object value) {
        try {
            interpreter.set(key, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object eval(String source) throws Exception {
        return interpreter.eval(source);
    }
}