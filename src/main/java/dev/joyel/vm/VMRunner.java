package dev.joyel.vm;

import de.xbrowniecodez.jbytemod.Main;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.util.Map;

public class VMRunner {

    private final Map<String, ClassNode> classes;

    public VMRunner(Map<String, ClassNode> classes) {
        this.classes = classes;
    }

    public VM buildVM(boolean noInit) {
        IVMReferenceHandler handler = internalName -> classes.get(internalName);

        VM vm = noInit
                ? VM.constructNonInitializingVM(handler)
                : VM.constructVM(handler);

        vm.setDummyLoading(true);

        for (ClassNode cn : classes.values()) {
            String dotName = cn.name.replace('/', '.');
            if (!vm.isLoaded(dotName)) {
                vm.explicitlyPreload(cn, noInit);
            }
        }

        return vm;
    }

    public Object invokeStatic(VM vm, String owner, String name, String desc, Object... args) throws Exception {
        String dotOwner = owner.replace('/', '.');
        Class<?> clazz = vm.loadClass(dotOwner, true);
        if (clazz == null) {
            throw new ClassNotFoundException("VM could not load: " + dotOwner);
        }

        Type[] argTypes = Type.getArgumentTypes(desc);
        Class<?>[] paramTypes = new Class<?>[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            paramTypes[i] = asmTypeToJavaClass(argTypes[i]);
            if (paramTypes[i] == null) {
                throw new IllegalArgumentException("Cannot map ASM type to Java class: " + argTypes[i]);
            }
        }

        Method method = null;
        try {
            method = clazz.getDeclaredMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == argTypes.length) {
                    method = m;
                    break;
                }
            }
        }

        if (method == null) {
            throw new NoSuchMethodException("No method " + name + desc + " in " + dotOwner);
        }

        method.setAccessible(true);
        return method.invoke(null, args);
    }

    public boolean isStaticMethod(ClassNode cn, String name, String desc) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) {
                return (mn.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0;
            }
        }
        return false;
    }

    private static Class<?> asmTypeToJavaClass(Type t) {
        switch (t.getSort()) {
            case Type.INT:     return int.class;
            case Type.LONG:    return long.class;
            case Type.FLOAT:   return float.class;
            case Type.DOUBLE:  return double.class;
            case Type.BOOLEAN: return boolean.class;
            case Type.BYTE:    return byte.class;
            case Type.CHAR:    return char.class;
            case Type.SHORT:   return short.class;
            case Type.OBJECT:
                switch (t.getClassName()) {
                    case "java.lang.String":  return String.class;
                    case "java.lang.Object":  return Object.class;
                    case "java.lang.Integer": return Integer.class;
                    case "java.lang.Long":    return Long.class;
                    case "java.lang.Float":   return Float.class;
                    case "java.lang.Double":  return Double.class;
                    case "java.lang.Boolean": return Boolean.class;
                    default:
                        try {
                            return Class.forName(t.getClassName());
                        } catch (ClassNotFoundException e) {
                            return null;
                        }
                }
            case Type.ARRAY:
                try {
                    return Class.forName(t.getDescriptor().replace('/', '.'));
                } catch (ClassNotFoundException e) {
                    return null;
                }
            default:
                return null;
        }
    }
}
