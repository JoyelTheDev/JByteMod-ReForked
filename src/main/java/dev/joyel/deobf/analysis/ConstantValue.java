package dev.joyel.deobf.analysis;

import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Objects;

public final class ConstantValue implements Value {

    public static final Object NULL_SENTINEL = new Object() {
        @Override public String toString() { return "null"; }
    };

    private final BasicValue type;
    Object value;

    ConstantValue(BasicValue type, Object value) {
        this.type = Objects.requireNonNull(type);
        if (type == BasicValue.INT_VALUE && value != null && !(value instanceof Integer)) {
            value = toInt(value);
        }
        this.value = value;
    }

    @Override
    public int getSize() { return type.getSize(); }

    public BasicValue getType() { return type; }

    public Object getValue() { return value; }

    public boolean isKnown() { return value != null; }

    public boolean isNull() { return value == NULL_SENTINEL; }

    public boolean isInteger() { return type == BasicValue.INT_VALUE; }

    public boolean isString() { return value instanceof String; }

    public boolean isLong() { return type == BasicValue.LONG_VALUE; }

    public Integer getAsInteger() {
        if (value == null) return null;
        return ((Number) value).intValue();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ConstantValue)) return false;
        ConstantValue that = (ConstantValue) obj;
        return Objects.equals(this.value, that.value) && Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return (value == null ? 7 : value.hashCode()) + type.hashCode() * 31;
    }

    @Override
    public String toString() {
        if (isNull()) return "null";
        return value == null ? "unknown" : value + " (" + type + ")";
    }

    private static Integer toInt(Object v) {
        if (v instanceof Character) return (int) (char) (Character) v;
        if (v instanceof Boolean) return ((Boolean) v) ? 1 : 0;
        if (v instanceof Number) return ((Number) v).intValue();
        return null;
    }
}
