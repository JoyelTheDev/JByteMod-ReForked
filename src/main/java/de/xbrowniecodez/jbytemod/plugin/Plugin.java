package de.xbrowniecodez.jbytemod.plugin;

import de.xbrowniecodez.jbytemod.Main;
import dev.joyel.plugins.PluginContext;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;

@Getter
@Setter
public abstract class Plugin {
    protected String name;
    protected String version;
    protected String author;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private PluginContext context;

    public Plugin(String name, String version, String author) {
        this.name = name;
        this.version = version;
        this.author = author;
    }

    public abstract void init();

    public void shutdown() {}

    public abstract void loadFile(Map<String, ClassNode> map);

    public abstract boolean isClickable();

    public abstract void menuClick();

    public final void attachContext(PluginContext context) {
        this.context = context;
    }

    public void classSelected(ClassNode node) {
    }

    public void methodSelected(ClassNode owner, MethodNode method) {
    }

    public void fileSaved(File file) {
    }

    protected final PluginContext context() {
        if (context == null) {
            throw new IllegalStateException("The plugin context is not available yet. Use it from init() or later.");
        }
        return context;
    }

    protected final void log(String message) {
        context().log(message);
    }

    protected final void toast(String message) {
        context().toast(message);
    }

    protected final Path dataFolder() {
        return context().dataFolder();
    }

    protected final String applicationVersion() {
        return context().applicationVersion();
    }

    protected final String setting(String key, String defaultValue) {
        return context().setting(key, defaultValue);
    }

    protected final void saveSetting(String key, String value) {
        context().saveSetting(key, value);
    }

    protected final Map<String, ClassNode> getCurrentFile() {
        return Main.INSTANCE.getJByteMod().getJarArchive().getClasses();
    }

    protected final void updateTree() {
        Main.INSTANCE.getJByteMod().refreshTree();
    }

    protected final JMenuBar getMenu() {
        return Main.INSTANCE.getJByteMod().getMyMenuBar();
    }

    protected final JTree getTree() {
        return Main.INSTANCE.getJByteMod().getJarTree();
    }

    protected final ClassNode getSelectedNode() {
        return Main.INSTANCE.getJByteMod().getCurrentNode();
    }

    protected final MethodNode getSelectedMethod() {
        return Main.INSTANCE.getJByteMod().getCurrentMethod();
    }
}
