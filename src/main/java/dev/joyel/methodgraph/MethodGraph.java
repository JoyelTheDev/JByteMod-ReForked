package dev.joyel.methodgraph;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MethodGraph {

    private final MethodKey root;
    private final Map<MethodKey, MethodNode> nodes;
    private final List<CallEdge> calls;
    private final Bounds bounds;

    public MethodGraph(MethodKey root, Map<MethodKey, MethodNode> nodes,
                       List<CallEdge> calls, Bounds bounds) {
        this.root   = root;
        this.nodes  = Collections.unmodifiableMap(nodes);
        this.calls  = Collections.unmodifiableList(calls);
        this.bounds = bounds;
    }

    public MethodKey root()                      { return root; }
    public Map<MethodKey, MethodNode> nodes()    { return nodes; }
    public List<CallEdge> calls()                { return calls; }
    public Bounds bounds()                       { return bounds; }

    public enum Direction {
        CALLS("Calls"),
        CALLERS("Callers"),
        BOTH("Both");

        private final String label;
        Direction(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public static final class MethodKey {
        private final String owner;
        private final String name;
        private final String descriptor;

        public MethodKey(String owner, String name, String descriptor) {
            this.owner      = owner;
            this.name       = name;
            this.descriptor = descriptor;
        }

        public String owner()      { return owner; }
        public String name()       { return name; }
        public String descriptor() { return descriptor; }

        public String displayOwner() {
            int sep = owner.lastIndexOf('/');
            return sep == -1 ? owner : owner.substring(sep + 1);
        }

        public String symbol() { return owner + '.' + name + descriptor; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodKey)) return false;
            MethodKey k = (MethodKey) o;
            return owner.equals(k.owner) && name.equals(k.name) && descriptor.equals(k.descriptor);
        }

        @Override
        public int hashCode() {
            int h = owner.hashCode();
            h = 31 * h + name.hashCode();
            h = 31 * h + descriptor.hashCode();
            return h;
        }

        @Override
        public String toString() { return symbol(); }
    }

    public static final class MethodNode {
        private final MethodKey key;
        private final org.objectweb.asm.tree.MethodNode asmNode;
        private final int depth;
        private final int rank;
        private final boolean root;
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        public MethodNode(MethodKey key, org.objectweb.asm.tree.MethodNode asmNode,
                          int depth, int rank, boolean root,
                          float x, float y, float width, float height) {
            this.key     = key;
            this.asmNode = asmNode;
            this.depth   = depth;
            this.rank    = rank;
            this.root    = root;
            this.x       = x;
            this.y       = y;
            this.width   = width;
            this.height  = height;
        }

        public MethodKey key()                          { return key; }
        public org.objectweb.asm.tree.MethodNode asmNode() { return asmNode; }
        public int depth()                              { return depth; }
        public int rank()                               { return rank; }
        public boolean root()                           { return root; }
        public float x()                                { return x; }
        public float y()                                { return y; }
        public float width()                            { return width; }
        public float height()                           { return height; }
        public boolean external()                       { return asmNode == null; }
    }

    public static final class CallEdge {
        private final MethodKey caller;
        private final MethodKey callee;
        private final int callSites;
        private final boolean dynamicDispatch;

        public CallEdge(MethodKey caller, MethodKey callee, int callSites, boolean dynamicDispatch) {
            this.caller          = caller;
            this.callee          = callee;
            this.callSites       = callSites;
            this.dynamicDispatch = dynamicDispatch;
        }

        public MethodKey caller()       { return caller; }
        public MethodKey callee()       { return callee; }
        public int callSites()          { return callSites; }
        public boolean dynamicDispatch(){ return dynamicDispatch; }
    }

    public static final class Bounds {
        private final float minX;
        private final float minY;
        private final float maxX;
        private final float maxY;

        public Bounds(float minX, float minY, float maxX, float maxY) {
            this.minX = minX; this.minY = minY;
            this.maxX = maxX; this.maxY = maxY;
        }

        public float minX()   { return minX; }
        public float minY()   { return minY; }
        public float maxX()   { return maxX; }
        public float maxY()   { return maxY; }
        public float width()  { return maxX - minX; }
        public float height() { return maxY - minY; }
    }
}
