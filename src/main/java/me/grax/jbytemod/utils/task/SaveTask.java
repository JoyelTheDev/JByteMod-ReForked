package me.grax.jbytemod.utils.task;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.ui.PageEndPanel;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.objectweb.asm.tree.ClassNode;

import de.xbrowniecodez.jbytemod.asm.CustomClassWriter;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SaveTask extends SwingWorker<Void, Integer> {

    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    private final File output;
    private final PageEndPanel jpb;
    private final JarArchive file;

    public SaveTask(JByteMod jbm, File output, JarArchive file) {
        this.output = output;
        this.file = file;
        this.jpb = jbm.getPageEndPanel();
    }

    @Override
    protected Void doInBackground() throws Exception {
        synchronized (this.file) {
            if (jpb != null) jpb.setTask("Saving");
            try {
                Map<String, ClassNode> classes = this.file.getClasses();
                Map<String, byte[]> outputBytes = this.file.getOutput();
                int flags = Main.INSTANCE.getJByteMod().getOptions().get("compute_maxs").getBoolean() ? 1 : 0;
                Main.INSTANCE.getLogger().log("Writing..");
                if (this.file.isSingleEntry()) {
                    saveSingleClass(classes, flags);
                    return null;
                }

                publish(0);
                Map<String, byte[]> compiled = new LinkedHashMap<>();
                List<String> failed = new ArrayList<>();
                double size = classes.size();
                double i = 0;
                for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
                    try {
                        compiled.put(entry.getKey() + ".class", writeClass(entry.getValue(), flags));
                    } catch (Exception | StackOverflowError ex) {
                        failed.add(entry.getKey());
                        Main.INSTANCE.getLogger().err("Failed to save " + entry.getKey() + ": " + ex);
                    }
                    publish((int) ((i++ / size) * 50d));
                }

                if (!failed.isEmpty()) {
                    Main.INSTANCE.getLogger().errNotification("Save aborted, " + failed.size() + " class(es) failed to write");
                    return null;
                }

                outputBytes.putAll(compiled);
                publish(50);
                Main.INSTANCE.getLogger().log("Saving..");
                saveAsJarNew(outputBytes, output.toPath());
                Main.INSTANCE.getLogger().successNotification("Saved " + this.output.getName());
            } catch (Exception e) {
                Main.INSTANCE.getLogger().errNotification("Saving failed: " + e.getMessage());
            }
            publish(100);
            return null;
        }
    }

    private byte[] writeClass(ClassNode node, int flags) {
        CustomClassWriter writer = new CustomClassWriter(flags);
        node.accept(writer);
        return writer.toByteArray();
    }

    private void saveSingleClass(Map<String, ClassNode> classes, int flags) throws IOException {
        ClassNode node = classes.values().iterator().next();
        byte[] bytes = writeClass(node, flags);
        publish(50);
        Main.INSTANCE.getLogger().log("Saving..");
        Files.write(toClassPath(output), bytes);
        publish(100);
        Main.INSTANCE.getLogger().successNotification("Saved " + toClassPath(output).getFileName());
    }

    static Path toClassPath(File target) {
        String name = target.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name = name.substring(0, name.length() - 4) + ".class";
        } else if (!name.toLowerCase(Locale.ROOT).endsWith(".class")) {
            name = name + ".class";
        }
        File parent = target.getAbsoluteFile().getParentFile();
        return new File(parent, name).toPath();
    }

    static Map<String, byte[]> orderEntries(Map<String, byte[]> source) {
        Map<String, byte[]> ordered = new LinkedHashMap<>();
        byte[] manifest = source.get(MANIFEST);
        if (manifest != null) {
            ordered.put(MANIFEST, manifest);
        }
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    public void saveAsJarNew(Map<String, byte[]> outBytes, Path target) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".jbm-save-", ".tmp");
        try {
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
                out.setEncoding("UTF-8");
                for (Map.Entry<String, byte[]> entry : orderEntries(outBytes).entrySet()) {
                    String name = entry.getKey();
                    out.putNextEntry(new ZipEntry(name));
                    if (!name.endsWith("/") && !name.endsWith("\\")) {
                        out.write(entry.getValue());
                    }
                    out.closeEntry();
                }
            }
            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size() - 1);
        jpb.setValue(i);
        super.process(chunks);
    }

}
