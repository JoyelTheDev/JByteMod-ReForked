package dev.joyel.constpool;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.ConstantDynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConstantScanner {

    private ConstantScanner() {}

    public static List<ConstantEntry> scan(Map<String, ClassNode> classes) {
        List<ConstantEntry> result = new ArrayList<ConstantEntry>();
        for (ClassNode cn : classes.values()) {
            scanClass(cn, result);
        }
        return result;
    }

    private static void scanClass(ClassNode cn, List<ConstantEntry> out) {
        scanAnnotations(cn, null, null, cn, out);

        if (cn.fields != null) {
            for (FieldNode fn : cn.fields) {
                if (fn.value != null) {
                    addValue(fn.value, cn, null, fn, out, false);
                }
                scanAnnotations(cn, null, fn, cn, out);
            }
        }

        if (cn.methods != null) {
            for (MethodNode mn : cn.methods) {
                scanAnnotations(cn, mn, null, cn, out);
                if (mn.instructions == null) continue;
                for (AbstractInsnNode insn : mn.instructions) {
                    scanInsn(insn, cn, mn, out);
                }
            }
        }
    }

    private static void scanInsn(AbstractInsnNode insn, ClassNode cn,
                                  MethodNode mn, List<ConstantEntry> out) {
        if (insn instanceof LdcInsnNode) {
            addValue(((LdcInsnNode) insn).cst, cn, mn, null, out, false);

        } else if (insn instanceof IntInsnNode) {
            addValue(((IntInsnNode) insn).operand, cn, mn, null, out, false);

        } else if (insn instanceof IincInsnNode) {
            addValue(((IincInsnNode) insn).incr, cn, mn, null, out, false);

        } else if (insn instanceof InsnNode) {
            int op = insn.getOpcode();
            Object decoded = decodeConstInsn(op);
            if (decoded != null) addValue(decoded, cn, mn, null, out, false);

        } else if (insn instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) insn;
            if (dyn.bsm != null) {
                addValue(dyn.bsm, cn, mn, null, out, false);
            }
            if (dyn.bsmArgs != null) {
                for (Object arg : dyn.bsmArgs) {
                    addValue(arg, cn, mn, null, out, false);
                }
            }

        } else if (insn instanceof LookupSwitchInsnNode) {
            for (Integer key : ((LookupSwitchInsnNode) insn).keys) {
                addValue(key, cn, mn, null, out, false);
            }

        } else if (insn instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode ts = (TableSwitchInsnNode) insn;
            for (int v = ts.min; v <= ts.max; v++) {
                addValue(v, cn, mn, null, out, false);
            }
        }
    }

    private static void scanAnnotations(ClassNode cn, MethodNode mn, FieldNode fn,
                                         ClassNode owner, List<ConstantEntry> out) {
        addAnnotationList(cn, mn, fn, getAnnotations(cn, mn, fn), out);
    }

    private static List<AnnotationNode> getAnnotations(ClassNode cn, MethodNode mn, FieldNode fn) {
        List<AnnotationNode> list = new ArrayList<AnnotationNode>();
        if (mn != null) {
            addAll(list, mn.visibleAnnotations);
            addAll(list, mn.invisibleAnnotations);
        } else if (fn != null) {
            addAll(list, fn.visibleAnnotations);
            addAll(list, fn.invisibleAnnotations);
        } else {
            addAll(list, cn.visibleAnnotations);
            addAll(list, cn.invisibleAnnotations);
        }
        return list;
    }

    private static void addAnnotationList(ClassNode cn, MethodNode mn, FieldNode fn,
                                           List<AnnotationNode> annotations,
                                           List<ConstantEntry> out) {
        for (AnnotationNode an : annotations) {
            if (an == null || an.values == null) continue;
            for (int i = 1; i < an.values.size(); i += 2) {
                addValue(an.values.get(i), cn, mn, fn, out, true);
            }
        }
    }

    private static void addValue(Object value, ClassNode cn, MethodNode mn,
                                  FieldNode fn, List<ConstantEntry> out, boolean annotation) {
        if (value == null) return;
        ConstantKind kind = kindOf(value, annotation);
        if (kind == null) return;
        String display = displayOf(value);
        if (display == null) return;
        String location = locationOf(cn, mn, fn);
        out.add(new ConstantEntry(value, display, kind, cn, mn, fn, location));
    }

    private static ConstantKind kindOf(Object value, boolean annotation) {
        if (annotation) return ConstantKind.ANNOTATION;
        if (value instanceof String)  return ConstantKind.STRING;
        if (value instanceof Integer || value instanceof Short
                || value instanceof Byte) return ConstantKind.INTEGER;
        if (value instanceof Long)    return ConstantKind.LONG;
        if (value instanceof Float)   return ConstantKind.FLOAT;
        if (value instanceof Double)  return ConstantKind.DOUBLE;
        if (value instanceof Type)    return ConstantKind.TYPE;
        if (value instanceof Handle)  return ConstantKind.HANDLE;
        if (value instanceof ConstantDynamic) return ConstantKind.CONST_DYNAMIC;
        return null;
    }

    static String displayOf(Object value) {
        if (value instanceof String)  return "\"" + escapeStr((String) value) + "\"";
        if (value instanceof Float)   return value + "F";
        if (value instanceof Long)    return value + "L";
        if (value instanceof Double)  return value + "D";
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return String.valueOf(((Number) value).intValue());
        }
        if (value instanceof Type) {
            Type t = (Type) value;
            return t.getSort() == Type.METHOD
                    ? "method-type " + t.getDescriptor()
                    : t.getClassName() + ".class";
        }
        if (value instanceof Handle) {
            Handle h = (Handle) value;
            return h.getOwner() + "." + h.getName() + h.getDesc()
                    + " (" + handleTagName(h.getTag())
                    + (h.isInterface() ? ", interface" : "") + ")";
        }
        if (value instanceof ConstantDynamic) {
            ConstantDynamic cd = (ConstantDynamic) value;
            return cd.getName() + " : " + cd.getDescriptor();
        }
        return null;
    }

    private static String locationOf(ClassNode cn, MethodNode mn, FieldNode fn) {
        String base = simpleName(cn.name);
        if (mn != null) return base + "." + mn.name + mn.desc;
        if (fn != null) return base + "." + fn.name;
        return base;
    }

    private static String simpleName(String internalName) {
        int i = internalName.lastIndexOf('/');
        return i == -1 ? internalName : internalName.substring(i + 1);
    }

    private static Object decodeConstInsn(int opcode) {
        switch (opcode) {
            case Opcodes.ICONST_M1: return -1;
            case Opcodes.ICONST_0:  return 0;
            case Opcodes.ICONST_1:  return 1;
            case Opcodes.ICONST_2:  return 2;
            case Opcodes.ICONST_3:  return 3;
            case Opcodes.ICONST_4:  return 4;
            case Opcodes.ICONST_5:  return 5;
            case Opcodes.LCONST_0:  return 0L;
            case Opcodes.LCONST_1:  return 1L;
            case Opcodes.FCONST_0:  return 0.0f;
            case Opcodes.FCONST_1:  return 1.0f;
            case Opcodes.FCONST_2:  return 2.0f;
            case Opcodes.DCONST_0:  return 0.0d;
            case Opcodes.DCONST_1:  return 1.0d;
            default:                return null;
        }
    }

    private static String handleTagName(int tag) {
        switch (tag) {
            case Opcodes.H_GETFIELD:         return "getfield";
            case Opcodes.H_GETSTATIC:        return "getstatic";
            case Opcodes.H_PUTFIELD:         return "putfield";
            case Opcodes.H_PUTSTATIC:        return "putstatic";
            case Opcodes.H_INVOKEVIRTUAL:    return "invokevirtual";
            case Opcodes.H_INVOKESTATIC:     return "invokestatic";
            case Opcodes.H_INVOKESPECIAL:    return "invokespecial";
            case Opcodes.H_NEWINVOKESPECIAL: return "newinvokespecial";
            case Opcodes.H_INVOKEINTERFACE:  return "invokeinterface";
            default:                         return "tag " + tag;
        }
    }

    private static String escapeStr(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void addAll(List<AnnotationNode> target, List<AnnotationNode> src) {
        if (src != null) target.addAll(src);
    }
}
