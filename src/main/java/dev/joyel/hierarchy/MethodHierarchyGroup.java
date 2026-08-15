package dev.joyel.hierarchy;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MethodHierarchyGroup {

    private final String name;
    private final String descriptor;
    private final Set<Entry> members = new LinkedHashSet<Entry>();

    MethodHierarchyGroup(String name, String descriptor) {
        this.name       = name;
        this.descriptor = descriptor;
    }

    void add(ClassNode owner, MethodNode method) {
        members.add(new Entry(owner, method));
    }

    public String getName()        { return name; }
    public String getDescriptor()  { return descriptor; }
    public Set<Entry> getMembers() { return members; }

    public int size() { return members.size(); }

    public static final class Entry {
        private final ClassNode  owner;
        private final MethodNode method;

        Entry(ClassNode owner, MethodNode method) {
            this.owner  = owner;
            this.method = method;
        }

        public ClassNode  getOwner()  { return owner; }
        public MethodNode getMethod() { return method; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Entry)) return false;
            Entry other = (Entry) o;
            return owner.name.equals(other.owner.name)
                    && method.name.equals(other.method.name)
                    && method.desc.equals(other.method.desc);
        }

        @Override
        public int hashCode() {
            int h = owner.name.hashCode();
            h = 31 * h + method.name.hashCode();
            h = 31 * h + method.desc.hashCode();
            return h;
        }
    }
}
