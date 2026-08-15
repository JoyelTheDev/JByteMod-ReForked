package dev.joyel.decompiler;

public final class NavigableToken {

    public enum Kind { CLASS, METHOD, FIELD }

    private final Kind kind;
    private final String owner;
    private final String name;
    private final String descriptor;
    private final int startOffset;
    private final int endOffset;

    public NavigableToken(Kind kind, String owner, String name, String descriptor,
                          int startOffset, int endOffset) {
        this.kind        = kind;
        this.owner       = owner;
        this.name        = name;
        this.descriptor  = descriptor;
        this.startOffset = startOffset;
        this.endOffset   = endOffset;
    }

    public Kind kind()        { return kind; }
    public String owner()     { return owner; }
    public String name()      { return name; }
    public String descriptor(){ return descriptor; }
    public int startOffset()  { return startOffset; }
    public int endOffset()    { return endOffset; }

    public boolean contains(int offset) {
        return offset >= startOffset && offset < endOffset;
    }

    public String displayName() {
        switch (kind) {
            case CLASS:  return owner.replace('/', '.');
            case METHOD: return owner.replace('/', '.') + "#" + name;
            case FIELD:  return owner.replace('/', '.') + "#" + name;
            default:     return name;
        }
    }
}
