package dev.joyel.methodgraph;

import java.util.List;
import java.util.Map;

public record MethodGraph(
        MethodKey root,
        Map<MethodKey, MethodNode> nodes,
        List<CallEdge> calls,
        Bounds bounds) {

    public MethodGraph {
        nodes = Map.copyOf(nodes);
        calls = List.copyOf(calls);
    }

    public enum Direction {
        CALLS("Calls"),
        CALLERS("Callers"),
        BOTH("Both");

        private final String label;

        Direction(String label) { this.label = label; }

        public String getLabel() { return label; }
    }

    public record MethodKey(String owner, String name, String descriptor) {
        public String displayOwner() {
            int sep = owner.lastIndexOf('/');
            return sep == -1 ? owner : owner.substring(sep + 1);
        }

        public String symbol() {
            return owner + '.' + name + descriptor;
        }
    }

    public record MethodNode(
            MethodKey key,
            org.objectweb.asm.tree.MethodNode asmNode,
            int depth,
            int rank,
            boolean root,
            float x,
            float y,
            float width,
            float height) {

        public boolean external() { return asmNode == null; }
    }

    public record CallEdge(MethodKey caller, MethodKey callee, int callSites, boolean dynamicDispatch) {}

    public record Bounds(float minX, float minY, float maxX, float maxY) {
        public float width()  { return maxX - minX; }
        public float height() { return maxY - minY; }
    }
}
