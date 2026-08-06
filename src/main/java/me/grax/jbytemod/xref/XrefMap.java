package me.grax.jbytemod.xref;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

import java.util.*;

public final class XrefMap {

    private final Map<String, ClassNode> classes;

    private final Map<MemberKey, List<XrefEntry>> memberRefs = new HashMap<>();
    private final Map<String, List<XrefEntry>> classRefs = new HashMap<>();

    public XrefMap(Map<String, ClassNode> classes) {
        this.classes = classes;
        build();
    }

    public void rebuild() {
        memberRefs.clear();
        classRefs.clear();
        build();
    }

    private void build() {
        for (ClassNode cn : classes.values()) {
            processClass(cn);
        }
    }

    private void processClass(ClassNode cn) {
        if (cn.superName != null) {
            addClassRef(cn.superName, new XrefEntry(
                    XrefKind.INHERIT, XrefAccessType.READ, "Extends",
                    simpleClassName(cn.name), cn, null));
        }

        if (cn.interfaces != null) {
            for (String itf : cn.interfaces) {
                addClassRef(itf, new XrefEntry(
                        XrefKind.INHERIT, XrefAccessType.READ, "Implements",
                        simpleClassName(cn.name), cn, null));
            }
        }

        processAnnotations(cn.visibleAnnotations, cn, null);
        processAnnotations(cn.invisibleAnnotations, cn, null);

        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                processMethod(cn, mn);
            }
        }
    }

    private void processMethod(ClassNode cn, MethodNode mn) {
        String whereText = simpleClassName(cn.name) + "." + mn.name;

        processDescriptorRefs(cn, mn, mn.desc, whereText);
        processAnnotations(mn.visibleAnnotations, cn, mn);
        processAnnotations(mn.invisibleAnnotations, cn, mn);
        processTryCatch(mn.tryCatchBlocks, cn, mn, whereText);

        if (mn.instructions == null) return;

        for (AbstractInsnNode insn : mn.instructions) {
            if (insn.getOpcode() < Opcodes.NOP) continue;

            if (insn instanceof MethodInsnNode min) {
                XrefAccessType access = XrefAccessType.EXECUTE;
                String opName = Printer.OPCODES[insn.getOpcode()];
                MemberKey key = new MemberKey(min.owner, min.name, min.desc);
                addMemberRef(key, new XrefEntry(
                        XrefKind.INVOKE, access, opName, whereText, cn, mn));
                addClassRef(min.owner, new XrefEntry(
                        XrefKind.INVOKE, access, opName, whereText, cn, mn));

            } else if (insn instanceof FieldInsnNode fin) {
                XrefAccessType access = (insn.getOpcode() == Opcodes.PUTFIELD
                        || insn.getOpcode() == Opcodes.PUTSTATIC)
                        ? XrefAccessType.WRITE : XrefAccessType.READ;
                String opName = Printer.OPCODES[insn.getOpcode()];
                MemberKey key = new MemberKey(fin.owner, fin.name, fin.desc);
                addMemberRef(key, new XrefEntry(
                        XrefKind.FIELD, access, opName, whereText, cn, mn));
                addClassRef(fin.owner, new XrefEntry(
                        XrefKind.FIELD, access, opName, whereText, cn, mn));

            } else if (insn instanceof TypeInsnNode tin) {
                String opName = Printer.OPCODES[insn.getOpcode()];
                String rawDesc = tin.desc;
                String internalName = rawDesc.startsWith("[") ? stripArrayPrefix(rawDesc) : rawDesc;
                addClassRef(internalName, new XrefEntry(
                        XrefKind.TYPE, XrefAccessType.READ, opName, whereText, cn, mn));

            } else if (insn instanceof LdcInsnNode ldc) {
                if (ldc.cst instanceof Type t && t.getSort() == Type.OBJECT) {
                    addClassRef(t.getInternalName(), new XrefEntry(
                            XrefKind.LITERAL, XrefAccessType.READ, ".class", whereText, cn, mn));
                }
            }
        }
    }

    private void processDescriptorRefs(ClassNode cn, MethodNode mn, String desc, String whereText) {
        try {
            Type retType = Type.getReturnType(desc);
            if (retType.getSort() == Type.OBJECT) {
                addClassRef(retType.getInternalName(), new XrefEntry(
                        XrefKind.RETURN, XrefAccessType.READ, "Returns", whereText, cn, mn));
            }
            for (Type argType : Type.getArgumentTypes(desc)) {
                Type resolved = argType.getSort() == Type.ARRAY
                        ? argType.getElementType() : argType;
                if (resolved.getSort() == Type.OBJECT) {
                    addClassRef(resolved.getInternalName(), new XrefEntry(
                            XrefKind.PARAMETER, XrefAccessType.READ, "Parameter", whereText, cn, mn));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void processTryCatch(List<TryCatchBlockNode> blocks, ClassNode cn, MethodNode mn, String whereText) {
        if (blocks == null) return;
        for (TryCatchBlockNode block : blocks) {
            if (block.type != null) {
                addClassRef(block.type, new XrefEntry(
                        XrefKind.EXCEPTION, XrefAccessType.READ, "Catch", whereText, cn, mn));
            }
        }
    }

    private void processAnnotations(List<AnnotationNode> annotations, ClassNode cn, MethodNode mn) {
        if (annotations == null) return;
        String whereText = mn != null
                ? simpleClassName(cn.name) + "." + mn.name
                : simpleClassName(cn.name);
        for (AnnotationNode an : annotations) {
            try {
                Type t = Type.getType(an.desc);
                if (t.getSort() == Type.OBJECT) {
                    addClassRef(t.getInternalName(), new XrefEntry(
                            XrefKind.ANNOTATION, XrefAccessType.READ,
                            "@" + simpleClassName(t.getInternalName()), whereText, cn, mn));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void addMemberRef(MemberKey key, XrefEntry entry) {
        memberRefs.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
    }

    private void addClassRef(String internalName, XrefEntry entry) {
        if (internalName == null || internalName.isEmpty()) return;
        String cleaned = cleanInternalName(internalName);
        classRefs.computeIfAbsent(cleaned, k -> new ArrayList<>()).add(entry);
    }

    public List<XrefEntry> getMemberRefs(String owner, String name, String desc) {
        List<XrefEntry> result = memberRefs.get(new MemberKey(owner, name, desc));
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    public List<XrefEntry> getClassRefs(String internalName) {
        List<XrefEntry> result = classRefs.get(cleanInternalName(internalName));
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    public int getMemberRefCount(String owner, String name, String desc) {
        return getMemberRefs(owner, name, desc).size();
    }

    public int getClassRefCount(String internalName) {
        return getClassRefs(internalName).size();
    }

    public Map<MemberKey, List<XrefEntry>> getAllMemberRefs() {
        return Collections.unmodifiableMap(memberRefs);
    }

    public Map<String, List<XrefEntry>> getAllClassRefs() {
        return Collections.unmodifiableMap(classRefs);
    }

    private static String simpleClassName(String internalName) {
        if (internalName == null) return "?";
        int slash = internalName.lastIndexOf('/');
        return slash == -1 ? internalName : internalName.substring(slash + 1);
    }

    private static String cleanInternalName(String raw) {
        if (raw == null) return "";
        String s = raw;
        while (s.startsWith("[")) s = s.substring(1);
        if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length() - 1);
        return s;
    }

    private static String stripArrayPrefix(String desc) {
        String s = desc;
        while (s.startsWith("[")) s = s.substring(1);
        if (s.startsWith("L") && s.endsWith(";")) return s.substring(1, s.length() - 1);
        return s;
    }
}
