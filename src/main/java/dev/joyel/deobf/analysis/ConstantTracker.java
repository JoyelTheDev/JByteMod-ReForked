package dev.joyel.deobf.analysis;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ConstantTracker extends Interpreter<ConstantValue> implements Opcodes {

    public static final ConstantValue NULL = new ConstantValue(BasicValue.REFERENCE_VALUE, ConstantValue.NULL_SENTINEL);

    private static final List<String> SAFE_CLASSES = Arrays.asList(
            "java/lang/Integer", "java/lang/Long", "java/lang/Short", "java/lang/Byte",
            "java/lang/Boolean", "java/lang/String", "java/lang/Float", "java/lang/Double",
            "java/lang/StringBuilder", "java/lang/StringBuffer"
    );

    private final BasicInterpreter basic = new BasicInterpreter();
    private final Object[] args;

    public ConstantTracker() {
        super(ASM9);
        this.args = new Object[0];
    }

    public ConstantTracker(boolean isStatic, int maxLocals, String desc, Object[] suppliedArgs) {
        super(ASM9);
        Type[] argTypes = Type.getArgumentTypes(desc);
        List<Object> formatted = new ArrayList<>();
        if (!isStatic) formatted.add(null);
        for (int i = 0; i < argTypes.length; i++) {
            formatted.add(i < suppliedArgs.length ? suppliedArgs[i] : null);
            if (argTypes[i].getSize() == 2) formatted.add(null);
        }
        while (formatted.size() < maxLocals) formatted.add(null);
        this.args = formatted.toArray();
    }

    @Override
    public ConstantValue newValue(Type type) {
        if (type == null) return new ConstantValue(BasicValue.UNINITIALIZED_VALUE, null);
        BasicValue bv = (BasicValue) basic.newValue(type);
        return bv == null ? null : new ConstantValue(bv, null);
    }

    @Override
    public ConstantValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
        BasicValue bv = (BasicValue) basic.newValue(type);
        if (bv == null) return null;
        int idx = local;
        Object val = (idx < args.length) ? args[idx] : null;
        return new ConstantValue(bv, val);
    }

    @Override
    public ConstantValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
        BasicValue bv = (BasicValue) basic.newOperation(insn);
        switch (insn.getOpcode()) {
            case ACONST_NULL: return NULL;
            case ICONST_M1: case ICONST_0: case ICONST_1: case ICONST_2:
            case ICONST_3: case ICONST_4: case ICONST_5:
                return new ConstantValue(BasicValue.INT_VALUE, insn.getOpcode() - ICONST_0);
            case LCONST_0: case LCONST_1:
                return new ConstantValue(BasicValue.LONG_VALUE, (long)(insn.getOpcode() - LCONST_0));
            case FCONST_0: case FCONST_1: case FCONST_2:
                return new ConstantValue(BasicValue.FLOAT_VALUE, (float)(insn.getOpcode() - FCONST_0));
            case DCONST_0: case DCONST_1:
                return new ConstantValue(BasicValue.DOUBLE_VALUE, (double)(insn.getOpcode() - DCONST_0));
            case BIPUSH: case SIPUSH:
                return new ConstantValue(BasicValue.INT_VALUE, ((IntInsnNode) insn).operand);
            case LDC:
                return new ConstantValue(bv == null ? BasicValue.REFERENCE_VALUE : bv, ((LdcInsnNode) insn).cst);
            default:
                return bv == null ? null : new ConstantValue(bv, null);
        }
    }

    @Override
    public ConstantValue copyOperation(AbstractInsnNode insn, ConstantValue value) {
        return value;
    }

    @Override
    public ConstantValue unaryOperation(AbstractInsnNode insn, ConstantValue value) throws AnalyzerException {
        BasicValue bv = (BasicValue) basic.unaryOperation(insn, value.getType());
        return bv == null ? null : new ConstantValue(bv, computeUnary(insn.getOpcode(), value));
    }

    @Override
    public ConstantValue binaryOperation(AbstractInsnNode insn, ConstantValue v1, ConstantValue v2) throws AnalyzerException {
        BasicValue bv = (BasicValue) basic.binaryOperation(insn, v1.getType(), v2.getType());
        return bv == null ? null : new ConstantValue(bv, computeBinary(insn.getOpcode(), v1, v2));
    }

    @Override
    public ConstantValue ternaryOperation(AbstractInsnNode insn, ConstantValue v1, ConstantValue v2, ConstantValue v3) throws AnalyzerException {
        return new ConstantValue(BasicValue.UNINITIALIZED_VALUE, null);
    }

    @Override
    public ConstantValue naryOperation(AbstractInsnNode insn, List<? extends ConstantValue> values) throws AnalyzerException {
        BasicValue bv = (BasicValue) basic.naryOperation(insn, toBasicList(values));
        if (bv == null) return null;
        Object simulated = trySimulateMethod(insn, values);
        return new ConstantValue(bv, simulated);
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, ConstantValue value, ConstantValue expected) {}

    @Override
    public ConstantValue merge(ConstantValue a, ConstantValue b) {
        if (a == b) return a;
        if (a == null || b == null) return a == null ? b : a;
        BasicValue mergedType = mergeBasic(a.getType(), b.getType());
        if (mergedType == null) return new ConstantValue(BasicValue.UNINITIALIZED_VALUE, null);
        Object mergedVal = (a.isKnown() && b.isKnown() && a.getValue().equals(b.getValue())) ? a.getValue() : null;
        return new ConstantValue(mergedType, mergedVal);
    }

    private BasicValue mergeBasic(BasicValue a, BasicValue b) {
        if (a.equals(b)) return a;
        if (a == BasicValue.UNINITIALIZED_VALUE || b == BasicValue.UNINITIALIZED_VALUE)
            return BasicValue.UNINITIALIZED_VALUE;
        if (a.isReference() && b.isReference()) return BasicValue.REFERENCE_VALUE;
        return BasicValue.UNINITIALIZED_VALUE;
    }

    private Object computeUnary(int opcode, ConstantValue v) {
        if (!v.isKnown()) return null;
        Object val = v.getValue();
        try {
            switch (opcode) {
                case INEG: return -(Integer) val;
                case LNEG: return -(Long) val;
                case FNEG: return -(Float) val;
                case DNEG: return -(Double) val;
                case I2L: return (long)((Number)val).intValue();
                case I2F: return (float)((Number)val).intValue();
                case I2D: return (double)((Number)val).intValue();
                case L2I: return ((Number)val).intValue();
                case L2F: return (float)((Number)val).longValue();
                case L2D: return (double)((Number)val).longValue();
                case F2I: return ((Number)val).intValue();
                case F2L: return (long)((Number)val).floatValue();
                case F2D: return (double)((Number)val).floatValue();
                case D2I: return ((Number)val).intValue();
                case D2L: return (long)((Number)val).doubleValue();
                case D2F: return (float)((Number)val).doubleValue();
                case I2B: return ((Number)val).byteValue();
                case I2C: return (int)(char)((Number)val).intValue();
                case I2S: return ((Number)val).shortValue();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Object computeBinary(int opcode, ConstantValue v1, ConstantValue v2) {
        if (!v1.isKnown() || !v2.isKnown()) return null;
        try {
            switch (opcode) {
                case IADD: return ((Number)v1.getValue()).intValue() + ((Number)v2.getValue()).intValue();
                case ISUB: return ((Number)v1.getValue()).intValue() - ((Number)v2.getValue()).intValue();
                case IMUL: return ((Number)v1.getValue()).intValue() * ((Number)v2.getValue()).intValue();
                case IDIV:
                    int d = ((Number)v2.getValue()).intValue();
                    return d == 0 ? null : ((Number)v1.getValue()).intValue() / d;
                case IREM:
                    int r = ((Number)v2.getValue()).intValue();
                    return r == 0 ? null : ((Number)v1.getValue()).intValue() % r;
                case ISHL: return ((Number)v1.getValue()).intValue() << ((Number)v2.getValue()).intValue();
                case ISHR: return ((Number)v1.getValue()).intValue() >> ((Number)v2.getValue()).intValue();
                case IUSHR: return ((Number)v1.getValue()).intValue() >>> ((Number)v2.getValue()).intValue();
                case IAND: return ((Number)v1.getValue()).intValue() & ((Number)v2.getValue()).intValue();
                case IOR:  return ((Number)v1.getValue()).intValue() | ((Number)v2.getValue()).intValue();
                case IXOR: return ((Number)v1.getValue()).intValue() ^ ((Number)v2.getValue()).intValue();
                case LADD: return ((Number)v1.getValue()).longValue() + ((Number)v2.getValue()).longValue();
                case LSUB: return ((Number)v1.getValue()).longValue() - ((Number)v2.getValue()).longValue();
                case LMUL: return ((Number)v1.getValue()).longValue() * ((Number)v2.getValue()).longValue();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Object trySimulateMethod(AbstractInsnNode insn, List<? extends ConstantValue> values) {
        if (insn.getType() != AbstractInsnNode.METHOD_INSN) return null;
        MethodInsnNode min = (MethodInsnNode) insn;
        if (!SAFE_CLASSES.contains(min.owner)) return null;
        if (values.stream().anyMatch(v -> !v.isKnown())) return null;
        try {
            Class<?> cls = Class.forName(min.owner.replace('/', '.'));
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals(min.name)) continue;
                if (Modifier.isStatic(m.getModifiers())) {
                    Object[] params = new Object[values.size()];
                    for (int i = 0; i < params.length; i++) params[i] = values.get(i).getValue();
                    return m.invoke(null, params);
                } else if (!values.isEmpty()) {
                    Object[] params = new Object[values.size() - 1];
                    for (int i = 0; i < params.length; i++) params[i] = values.get(i + 1).getValue();
                    return m.invoke(values.get(0).getValue(), params);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<BasicValue> toBasicList(List<? extends ConstantValue> values) {
        List<BasicValue> out = new ArrayList<>(values.size());
        for (ConstantValue v : values) out.add(v.getType());
        return out;
    }
}
