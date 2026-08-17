package dev.joyel.mapping;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MappingApplier {

    private MappingApplier() {}

    public static Map<String, ClassNode> apply(Map<String, ClassNode> classes, MappingSet mappings) {
        JbmRemapper remapper = new JbmRemapper(mappings);
        Map<String, ClassNode> result = new LinkedHashMap<String, ClassNode>();
        List<String> errors = new ArrayList<String>();

        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassNode original = entry.getValue();
            try {
                ClassWriter cw = new ClassWriter(0);
                ClassRemapper cr = new ClassRemapper(cw, remapper);
                original.accept(cr);

                ClassNode remapped = new ClassNode();
                new ClassReader(cw.toByteArray()).accept(remapped, 0);

                String newKey = mappings.hasClass(original.name)
                        ? mappings.getClass(original.name)
                        : original.name;
                result.put(newKey, remapped);
            } catch (Exception e) {
                errors.add(original.name + ": " + e.getMessage());
                result.put(original.name, original);
            }
        }

        if (!errors.isEmpty()) {
            System.err.println("[MappingApplier] Errors during remapping:");
            for (String err : errors) System.err.println("  " + err);
        }

        return result;
    }

    private static final class JbmRemapper extends Remapper {
        private final MappingSet mappings;

        JbmRemapper(MappingSet mappings) {
            this.mappings = mappings;
        }

        @Override
        public String map(String internalName) {
            String mapped = mappings.getClass(internalName);
            return mapped != null ? mapped : internalName;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String mapped = mappings.getField(owner, name, descriptor);
            if (mapped == null) mapped = mappings.getField(owner, name, "");
            return mapped != null ? mapped : name;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            String mapped = mappings.getMethod(owner, name, descriptor);
            return mapped != null ? mapped : name;
        }
    }
}
