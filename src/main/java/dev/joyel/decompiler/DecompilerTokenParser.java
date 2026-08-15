package dev.joyel.decompiler;

import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DecompilerTokenParser {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^import\\s+([\\w.]+(?:\\.[A-Z][\\w$]*)+)\\s*;", Pattern.MULTILINE);

    private static final Pattern SIMPLE_NAME_PATTERN =
            Pattern.compile("\\b([A-Z][A-Za-z0-9$_]*)\\b");

    private static final Pattern METHOD_CALL_PATTERN =
            Pattern.compile("\\b([A-Z][A-Za-z0-9$_]*)\\s*\\.\\s*([a-z_$][A-Za-z0-9$_]*)\\s*\\(");

    private static final Pattern FIELD_ACCESS_PATTERN =
            Pattern.compile("\\b([A-Z][A-Za-z0-9$_]*)\\s*\\.\\s*([A-Z_$][A-Za-z0-9$_]*)\\b(?!\\s*[\\.(])");

    private static final Pattern METHOD_DEF_PATTERN =
            Pattern.compile("(?:public|protected|private|static|final|abstract|synchronized|native|\\s)+" +
                    "[A-Za-z0-9$_<>\\[\\]]+\\s+([a-z_$][A-Za-z0-9$_]*)\\s*\\(");

    private final JarArchive archive;
    private final Map<String, String> simpleToInternal = new HashMap<>();

    public DecompilerTokenParser(JarArchive archive) {
        this.archive = archive;
    }

    public List<NavigableToken> parse(String source, String currentClassName) {
        buildImportMap(source, currentClassName);
        List<NavigableToken> tokens = new ArrayList<>();
        parseMethodCalls(source, tokens);
        parseFieldAccesses(source, tokens);
        parseClassRefs(source, tokens);
        tokens.sort(new java.util.Comparator<NavigableToken>() {
            public int compare(NavigableToken a, NavigableToken b) {
                return Integer.compare(a.startOffset(), b.startOffset());
            }
        });
        return deduplicate(tokens);
    }

    private void buildImportMap(String source, String currentClassName) {
        simpleToInternal.clear();
        if (currentClassName != null) {
            String simple = currentClassName.contains("/")
                    ? currentClassName.substring(currentClassName.lastIndexOf('/') + 1)
                    : currentClassName;
            simpleToInternal.put(simple, currentClassName);
        }
        Matcher m = IMPORT_PATTERN.matcher(source);
        while (m.find()) {
            String fqn = m.group(1);
            String internalName = fqn.replace('.', '/');
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
            simpleToInternal.put(simple, internalName);
        }
        if (archive != null && archive.getClasses() != null) {
            for (String name : archive.getClasses().keySet()) {
                String simple = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                if (!simpleToInternal.containsKey(simple)) {
                    simpleToInternal.put(simple, name);
                }
            }
        }
    }

    private void parseMethodCalls(String source, List<NavigableToken> tokens) {
        Matcher m = METHOD_CALL_PATTERN.matcher(source);
        while (m.find()) {
            String simpleName  = m.group(1);
            String methodName  = m.group(2);
            String internalOwner = simpleToInternal.get(simpleName);
            if (internalOwner == null) continue;
            ClassNode cn = resolveClass(internalOwner);
            if (cn == null) continue;
            MethodNode mn = findMethod(cn, methodName);
            if (mn == null) continue;
            int start = m.start(2);
            int end   = start + methodName.length();
            tokens.add(new NavigableToken(NavigableToken.Kind.METHOD,
                    cn.name, mn.name, mn.desc, start, end));
        }
    }

    private void parseFieldAccesses(String source, List<NavigableToken> tokens) {
        Matcher m = FIELD_ACCESS_PATTERN.matcher(source);
        while (m.find()) {
            String simpleName = m.group(1);
            String fieldName  = m.group(2);
            String internalOwner = simpleToInternal.get(simpleName);
            if (internalOwner == null) continue;
            ClassNode cn = resolveClass(internalOwner);
            if (cn == null) continue;
            FieldNode fn = findField(cn, fieldName);
            if (fn == null) continue;
            int start = m.start(2);
            int end   = start + fieldName.length();
            tokens.add(new NavigableToken(NavigableToken.Kind.FIELD,
                    cn.name, fn.name, fn.desc, start, end));
        }
    }

    private void parseClassRefs(String source, List<NavigableToken> tokens) {
        Matcher m = SIMPLE_NAME_PATTERN.matcher(source);
        while (m.find()) {
            String simpleName = m.group(1);
            String internal   = simpleToInternal.get(simpleName);
            if (internal == null) continue;
            ClassNode cn = resolveClass(internal);
            if (cn == null) continue;
            tokens.add(new NavigableToken(NavigableToken.Kind.CLASS,
                    cn.name, cn.name, null, m.start(), m.end()));
        }
    }

    private ClassNode resolveClass(String internalName) {
        if (archive == null || archive.getClasses() == null) return null;
        return archive.getClasses().get(internalName);
    }

    private MethodNode findMethod(ClassNode cn, String name) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name)) return mn;
        }
        return null;
    }

    private FieldNode findField(ClassNode cn, String name) {
        for (FieldNode fn : cn.fields) {
            if (fn.name.equals(name)) return fn;
        }
        return null;
    }

    private List<NavigableToken> deduplicate(List<NavigableToken> sorted) {
        List<NavigableToken> result = new ArrayList<>();
        int lastEnd = -1;
        for (NavigableToken t : sorted) {
            if (t.startOffset() >= lastEnd) {
                result.add(t);
                lastEnd = t.endOffset();
            }
        }
        return result;
    }
}
