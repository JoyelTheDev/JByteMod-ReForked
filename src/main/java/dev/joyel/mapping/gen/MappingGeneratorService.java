package dev.joyel.mapping.gen;

import dev.joyel.mapping.MappingSet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public final class MappingGeneratorService {

    private MappingGeneratorService() {}

    public static MappingSet generate(Map<String, ClassNode> classes,
                                      NameGenerator generator,
                                      boolean renameClasses,
                                      boolean renameFields,
                                      boolean renameMethods,
                                      boolean skipSpecialMethods) {
        generator.reset();
        MappingSet result = new MappingSet();

        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassNode cn = entry.getValue();

            if (renameClasses && isObfuscatedName(cn.name)) {
                String simpleName = cn.name.contains("/")
                        ? cn.name.substring(cn.name.lastIndexOf('/') + 1)
                        : cn.name;
                String pkg = cn.name.contains("/")
                        ? cn.name.substring(0, cn.name.lastIndexOf('/') + 1)
                        : "";
                result.putClass(cn.name, pkg + generator.nextClassName(simpleName));
            }

            if (renameFields) {
                for (FieldNode fn : cn.fields) {
                    if (isObfuscatedName(fn.name)) {
                        result.putField(cn.name, fn.name, fn.desc,
                                generator.nextFieldName(cn.name, fn.name, fn.desc));
                    }
                }
            }

            if (renameMethods) {
                for (MethodNode mn : cn.methods) {
                    if (skipSpecialMethods && isSpecialMethod(mn.name)) continue;
                    if (isObfuscatedName(mn.name)) {
                        result.putMethod(cn.name, mn.name, mn.desc,
                                generator.nextMethodName(cn.name, mn.name, mn.desc));
                    }
                }
            }
        }

        return result;
    }

    private static boolean isObfuscatedName(String name) {
        if (name == null || name.isEmpty()) return false;
        String simple = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        if (simple.length() <= 3) return true;
        long nonAlpha = simple.chars().filter(c -> !Character.isLetterOrDigit(c) && c != '_').count();
        if (nonAlpha > 0) return true;
        boolean allLower = simple.chars().allMatch(c -> !Character.isUpperCase(c));
        if (simple.length() <= 5 && allLower) return true;
        return false;
    }

    private static boolean isSpecialMethod(String name) {
        return name.equals("<init>") || name.equals("<clinit>") || name.startsWith("lambda$");
    }
}
