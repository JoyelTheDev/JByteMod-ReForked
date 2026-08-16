package dev.joyel.constpool;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class ConstantEntry {

    private final Object rawValue;
    private final String display;
    private final ConstantKind kind;
    private final ClassNode ownerClass;
    private final MethodNode ownerMethod;
    private final FieldNode ownerField;
    private final String locationText;

    public ConstantEntry(Object rawValue, String display, ConstantKind kind,
                         ClassNode ownerClass, MethodNode ownerMethod,
                         FieldNode ownerField, String locationText) {
        this.rawValue    = rawValue;
        this.display     = display;
        this.kind        = kind;
        this.ownerClass  = ownerClass;
        this.ownerMethod = ownerMethod;
        this.ownerField  = ownerField;
        this.locationText = locationText;
    }

    public Object getRawValue()      { return rawValue; }
    public String getDisplay()       { return display; }
    public ConstantKind getKind()    { return kind; }
    public ClassNode getOwnerClass() { return ownerClass; }
    public MethodNode getOwnerMethod() { return ownerMethod; }
    public FieldNode getOwnerField() { return ownerField; }
    public String getLocationText()  { return locationText; }

    public boolean matches(String query, boolean caseSensitive) {
        if (query == null || query.isEmpty()) return true;
        String d = caseSensitive ? display : display.toLowerCase(java.util.Locale.ROOT);
        String q = caseSensitive ? query   : query.toLowerCase(java.util.Locale.ROOT);
        return d.contains(q) || locationText.contains(caseSensitive ? query : q);
    }
}
