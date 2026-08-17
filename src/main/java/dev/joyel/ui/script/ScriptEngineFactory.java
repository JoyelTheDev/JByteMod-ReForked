package dev.joyel.ui.script;

import java.util.ArrayList;
import java.util.List;

public final class ScriptEngineFactory {

    private ScriptEngineFactory() {}

    public static List<JbmScriptEngine> availableEngines() {
        List<JbmScriptEngine> engines = new ArrayList<>();
        try {
            Class.forName("groovy.lang.GroovyShell");
            engines.add(new GroovyEngine());
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("bsh.Interpreter");
            engines.add(new BshEngine());
        } catch (ClassNotFoundException ignored) {}

        return engines;
    }
}