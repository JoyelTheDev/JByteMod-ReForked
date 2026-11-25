package dev.joyel.nbridge;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NativeScanner {
    private static final String[] LIBRARY_EXTENSIONS = {".dll", ".so", ".dylib", ".jnilib"};

    private NativeScanner() {
    }

    public static NativeScan scan(Map<String, ClassNode> classes, Map<String, byte[]> resources) {
        List<NativeLibrary> libraries = findLibraries(resources);
        List<LoadCall> loadCalls = new ArrayList<>();
        Map<String, Set<String>> hintsByClass = new HashMap<>();
        Set<String> globalHints = new LinkedHashSet<>();
        List<ClassNode> sortedClasses = new ArrayList<>(classes.values());
        sortedClasses.sort(Comparator.comparing(node -> node.name));

        for (ClassNode node : sortedClasses) {
            if (node.methods == null) {
                continue;
            }
            for (MethodNode method : node.methods) {
                collectLoadCalls(node, method, loadCalls, hintsByClass, globalHints);
            }
        }

        List<NativeMethodInfo> methods = new ArrayList<>();
        Map<String, Integer> overloadCounts = new HashMap<>();
        for (ClassNode node : sortedClasses) {
            if (node.methods == null) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if ((method.access & Opcodes.ACC_NATIVE) != 0) {
                    overloadCounts.merge(node.name + "." + method.name, 1, Integer::sum);
                }
            }
        }

        Map<NativeLibrary, Map<String, NativeExport>> exportMaps = new HashMap<>();
        for (NativeLibrary library : libraries) {
            Map<String, NativeExport> map = new HashMap<>();
            for (NativeExport export : library.exports()) {
                map.putIfAbsent(JniNames.normalizeExport(export.name(), library), export);
            }
            exportMaps.put(library, map);
        }

        Set<String> matchedExportKeys = new HashSet<>();
        for (ClassNode node : sortedClasses) {
            if (node.methods == null) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if ((method.access & Opcodes.ACC_NATIVE) == 0) {
                    continue;
                }
                String shortName = JniNames.shortName(node.name, method.name);
                String longName = JniNames.longName(node.name, method.name, method.desc);
                boolean overloaded = overloadCounts.getOrDefault(node.name + "." + method.name, 0) > 1;
                Set<String> hints = new LinkedHashSet<>(hintsByClass.getOrDefault(node.name, Set.of()));
                Set<String> effectiveHints = hints.isEmpty() ? globalHints : hints;
                List<NativeMatch> matches = new ArrayList<>();
                for (NativeLibrary library : libraries) {
                    boolean hinted = isHinted(library, effectiveHints);
                    Map<String, NativeExport> exports = exportMaps.get(library);
                    NativeExport shortExport = exports.get(shortName);
                    NativeExport longExport = exports.get(longName);
                    if (shortExport != null) {
                        matches.add(new NativeMatch(library, shortExport, false, hinted, overloaded));
                        matchedExportKeys.add(library.path() + "|" + shortExport.name());
                    }
                    if (longExport != null && longExport != shortExport) {
                        matches.add(new NativeMatch(library, longExport, true, hinted, false));
                        matchedExportKeys.add(library.path() + "|" + longExport.name());
                    }
                }
                matches.sort(Comparator.comparing((NativeMatch match) -> !match.hinted())
                        .thenComparing(match -> match.library().path()));
                MatchStatus status;
                if (!matches.isEmpty()) {
                    status = MatchStatus.EXPORTED;
                } else if (libraries.isEmpty()) {
                    status = MatchStatus.NO_LIBRARY;
                } else if (hasOnLoad(libraries, effectiveHints)) {
                    status = MatchStatus.DYNAMIC;
                } else {
                    status = MatchStatus.NOT_FOUND;
                }
                methods.add(new NativeMethodInfo(node, method, shortName, longName,
                        List.copyOf(hints), List.copyOf(matches), status));
            }
        }

        List<OrphanExport> orphans = findOrphans(libraries, matchedExportKeys, classes);
        return new NativeScan(List.copyOf(methods), List.copyOf(libraries), List.copyOf(orphans), List.copyOf(loadCalls));
    }

    private static boolean hasOnLoad(List<NativeLibrary> libraries, Set<String> hints) {
        boolean hintedFound = false;
        for (NativeLibrary library : libraries) {
            if (isHinted(library, hints) && library.jniOnLoad()) {
                hintedFound = true;
            }
        }
        if (hintedFound) {
            return true;
        }
        for (NativeLibrary library : libraries) {
            if (library.jniOnLoad()) {
                return true;
            }
        }
        return false;
    }

    private static List<NativeLibrary> findLibraries(Map<String, byte[]> resources) {
        List<NativeLibrary> libraries = new ArrayList<>();
        if (resources == null) {
            return libraries;
        }
        List<String> paths = new ArrayList<>(resources.keySet());
        paths.sort(String::compareTo);
        for (String path : paths) {
            if (path.endsWith(".class") || path.endsWith("/")) {
                continue;
            }
            byte[] data = resources.get(path);
            if (data == null) {
                continue;
            }
            if (!NativeLibraryParser.looksNative(data)) {
                continue;
            }
            NativeLibrary library = NativeLibraryParser.parse(path, data);
            if (library != null) {
                libraries.add(library);
            }
        }
        return libraries;
    }

    private static void collectLoadCalls(ClassNode node, MethodNode method, List<LoadCall> loadCalls,
                                         Map<String, Set<String>> hintsByClass, Set<String> globalHints) {
        if (method.instructions == null) {
            return;
        }
        String previousString = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() < 0) {
                continue;
            }
            if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String text) {
                previousString = text;
                continue;
            }
            if (instruction instanceof MethodInsnNode call) {
                String api = classify(call);
                if (api != null && previousString != null) {
                    loadCalls.add(new LoadCall(node.name, method.name, previousString, api));
                    hintsByClass.computeIfAbsent(node.name, key -> new LinkedHashSet<>()).add(previousString);
                    globalHints.add(previousString);
                }
            }
            previousString = null;
        }
    }

    private static String classify(MethodInsnNode call) {
        boolean loader = call.name.equals("loadLibrary") || call.name.equals("load");
        if (loader && call.desc.equals("(Ljava/lang/String;)V")) {
            if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("java/lang/System")) {
                return "System." + call.name;
            }
            if (call.owner.equals("java/lang/Runtime")) {
                return "Runtime." + call.name;
            }
        }
        if (call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals("java/lang/System")
                && call.name.equals("mapLibraryName")) {
            return "System.mapLibraryName";
        }
        return null;
    }

    static String libraryBase(String name) {
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.toLowerCase(Locale.ROOT);
        for (String extension : LIBRARY_EXTENSIONS) {
            int index = base.indexOf(extension);
            if (index > 0) {
                base = base.substring(0, index);
                break;
            }
        }
        if (base.startsWith("lib") && base.length() > 3) {
            base = base.substring(3);
        }
        return base;
    }

    static boolean isHinted(NativeLibrary library, Set<String> hints) {
        if (hints.isEmpty()) {
            return false;
        }
        String base = libraryBase(library.path());
        for (String hint : hints) {
            String wanted = libraryBase(hint);
            if (wanted.isEmpty()) {
                continue;
            }
            if (base.equals(wanted) || base.startsWith(wanted + "-") || base.startsWith(wanted + "_")
                    || base.startsWith(wanted + "64") || base.startsWith(wanted + "32")) {
                return true;
            }
        }
        return false;
    }

    private static List<OrphanExport> findOrphans(List<NativeLibrary> libraries, Set<String> matchedExportKeys,
                                                  Map<String, ClassNode> classes) {
        Map<String, ClassNode> byMangled = new HashMap<>();
        for (ClassNode node : classes.values()) {
            byMangled.putIfAbsent(JniNames.mangleClass(node.name), node);
        }
        List<OrphanExport> orphans = new ArrayList<>();
        for (NativeLibrary library : libraries) {
            for (NativeExport export : library.exports()) {
                String normalized = JniNames.normalizeExport(export.name(), library);
                if (!normalized.startsWith("Java_") || matchedExportKeys.contains(library.path() + "|" + export.name())) {
                    continue;
                }
                JniNames.Demangled demangled = JniNames.demangle(normalized);
                ClassNode owner = findOwner(normalized, byMangled);
                boolean classPresent = owner != null;
                boolean methodNamePresent = false;
                String className = demangled == null ? null : demangled.className();
                String methodName = demangled == null ? null : demangled.methodName();
                if (owner != null) {
                    className = owner.name.replace('/', '.');
                    String prefix = "Java_" + JniNames.mangleClass(owner.name) + "_";
                    String rest = normalized.substring(prefix.length());
                    int longSplit = rest.indexOf("__");
                    String mangledMethod = longSplit >= 0 ? rest.substring(0, longSplit) : rest;
                    if (owner.methods != null) {
                        for (MethodNode method : owner.methods) {
                            if (JniNames.mangle(method.name, false).equals(mangledMethod)) {
                                methodNamePresent = true;
                                methodName = method.name;
                                break;
                            }
                        }
                    }
                    if (!methodNamePresent && demangled != null) {
                        methodName = demangled.methodName();
                    }
                }
                orphans.add(new OrphanExport(library, export, className, methodName,
                        demangled == null ? null : demangled.arguments(), classPresent, methodNamePresent));
            }
        }
        orphans.sort(Comparator.comparing((OrphanExport orphan) -> orphan.library().path())
                .thenComparing(orphan -> orphan.export().name()));
        return orphans;
    }

    private static ClassNode findOwner(String exportName, Map<String, ClassNode> byMangled) {
        String body = exportName.substring(5);
        ClassNode best = null;
        int bestLength = -1;
        int index = body.indexOf('_');
        while (index > 0) {
            String candidate = body.substring(0, index);
            ClassNode node = byMangled.get(candidate);
            if (node != null && candidate.length() > bestLength) {
                best = node;
                bestLength = candidate.length();
            }
            index = body.indexOf('_', index + 1);
        }
        return best;
    }
}
