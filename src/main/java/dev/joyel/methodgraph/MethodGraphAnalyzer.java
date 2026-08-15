package dev.joyel.methodgraph;

import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class MethodGraphAnalyzer {

    public static final int INFINITE_DEPTH = -1;

    private static final float METHOD_WIDTH_MIN = 210f;
    private static final float METHOD_WIDTH_MAX = 520f;
    private static final float METHOD_HEIGHT    = 60f;
    private static final float METHOD_GAP_X     = 200f;
    private static final float METHOD_GAP_Y     = 90f;

    private final JarArchive archive;
    private Map<MethodGraph.MethodKey, List<Link>> reverseCache;
    private final Map<MethodGraph.MethodKey, List<Link>> outgoingCache = new HashMap<>();

    public MethodGraphAnalyzer(JarArchive archive) {
        this.archive = Objects.requireNonNull(archive);
    }

    public MethodGraph analyze(String owner, MethodNode root,
                               Request request, BooleanSupplier cancelled) {
        MethodGraph.MethodKey rootKey = new MethodGraph.MethodKey(owner, root.name, root.desc);

        Map<MethodGraph.MethodKey, Discovered> discovered = new LinkedHashMap<>();
        ArrayDeque<MethodGraph.MethodKey> queue = new ArrayDeque<>();
        discovered.put(rootKey, new Discovered(owner, root, 0, 0));
        queue.add(rootKey);

        while (!queue.isEmpty()) {
            checkCancelled(cancelled);
            MethodGraph.MethodKey current = queue.removeFirst();
            Discovered d = discovered.get(current);
            if (request.depth() != INFINITE_DEPTH && d.depth() >= request.depth()) continue;

            if (request.direction() != MethodGraph.Direction.CALLERS && d.asmNode() != null) {
                for (Link link : outgoing(d.owner(), d.asmNode(), request.includeExternal())) {
                    discover(discovered, queue, link.targetOwner(), link.targetNode(),
                            link.targetKey(), d.depth() + 1, d.rank() + 1);
                }
            }
            if (request.direction() != MethodGraph.Direction.CALLS) {
                for (Link link : reverseLinks(cancelled).getOrDefault(current, Collections.<Link>emptyList())) {
                    discover(discovered, queue, link.sourceOwner(), link.sourceNode(),
                            link.sourceKey(), d.depth() + 1, d.rank() - 1);
                }
            }
        }

        Map<EdgeKey, MutableEdge> edges = new LinkedHashMap<>();
        for (Map.Entry<MethodGraph.MethodKey, Discovered> entry : discovered.entrySet()) {
            checkCancelled(cancelled);
            Discovered d = entry.getValue();
            if (d.asmNode() == null) continue;
            for (Link link : outgoing(d.owner(), d.asmNode(), request.includeExternal())) {
                if (!discovered.containsKey(link.targetKey())) continue;
                EdgeKey ek = new EdgeKey(entry.getKey(), link.targetKey());
                MutableEdge me = edges.get(ek);
                if (me == null) { me = new MutableEdge(); edges.put(ek, me); }
                me.add(link.dynamic());
            }
        }

        Map<MethodGraph.MethodKey, NodeDraft> drafts = new LinkedHashMap<>();
        for (Map.Entry<MethodGraph.MethodKey, Discovered> entry : discovered.entrySet()) {
            checkCancelled(cancelled);
            Discovered d = entry.getValue();
            float w = nodeWidth(entry.getKey());
            drafts.put(entry.getKey(), new NodeDraft(entry.getKey(), d, w, METHOD_HEIGHT));
        }

        Map<MethodGraph.MethodKey, MethodGraph.MethodNode> nodes = layout(rootKey, drafts);

        List<MethodGraph.CallEdge> calls = new ArrayList<>();
        for (Map.Entry<EdgeKey, MutableEdge> entry : edges.entrySet()) {
            calls.add(new MethodGraph.CallEdge(
                    entry.getKey().caller(), entry.getKey().callee(),
                    entry.getValue().count, entry.getValue().dynamic));
        }
        calls.sort(Comparator.comparing(c -> c.caller().symbol()));

        return new MethodGraph(rootKey, nodes, calls, bounds(nodes));
    }

    private void discover(Map<MethodGraph.MethodKey, Discovered> discovered,
                          ArrayDeque<MethodGraph.MethodKey> queue,
                          String owner, MethodNode node,
                          MethodGraph.MethodKey key, int depth, int rank) {
        Discovered prev = discovered.get(key);
        if (prev != null && prev.depth() <= depth) return;
        discovered.put(key, new Discovered(owner, node, depth, rank));
        queue.addLast(key);
    }

    private List<Link> outgoing(String owner, MethodNode method, boolean includeExternal) {
        MethodGraph.MethodKey k = new MethodGraph.MethodKey(owner, method.name, method.desc);
        List<Link> all = outgoingCache.get(k);
        if (all == null) {
            all = scanOutgoing(owner, method);
            outgoingCache.put(k, all);
        }
        if (includeExternal) return all;
        List<Link> filtered = new ArrayList<>();
        for (Link l : all) { if (l.targetNode() != null) filtered.add(l); }
        return filtered;
    }

    private List<Link> scanOutgoing(String callerOwner, MethodNode method) {
        Map<MethodGraph.MethodKey, MutableLink> links = new LinkedHashMap<>();
        if (method.instructions == null) return Collections.emptyList();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode m = (MethodInsnNode) insn;
                addMethodLink(m.owner, m.name, m.desc, false, links);
            } else if (insn instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) insn;
                for (Object arg : dyn.bsmArgs) {
                    if (arg instanceof Handle) {
                        Handle h = (Handle) arg;
                        if (h.getDesc().startsWith("(")) {
                            addMethodLink(h.getOwner(), h.getName(), h.getDesc(), true, links);
                        }
                    }
                }
            }
        }
        List<Link> result = new ArrayList<>();
        for (MutableLink ml : links.values()) result.add(ml.freeze());
        return result;
    }

    private void addMethodLink(String owner, String name, String desc, boolean dynamic,
                               Map<MethodGraph.MethodKey, MutableLink> links) {
        MethodGraph.MethodKey key = new MethodGraph.MethodKey(owner, name, desc);
        MethodNode resolved = resolveMethod(owner, name, desc);
        String resolvedOwner = resolved != null ? findOwner(name, desc) : owner;
        MutableLink ml = links.get(key);
        if (ml == null) {
            ml = new MutableLink(resolvedOwner, resolved, key);
            links.put(key, ml);
        }
        ml.add(dynamic);
    }

    private MethodNode resolveMethod(String owner, String name, String desc) {
        if (archive == null || archive.getClasses() == null) return null;
        ClassNode cn = archive.getClasses().get(owner);
        if (cn == null) return null;
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && mn.desc.equals(desc)) return mn;
        }
        return null;
    }

    private String findOwner(String name, String desc) {
        if (archive == null || archive.getClasses() == null) return "?";
        for (ClassNode cn : archive.getClasses().values()) {
            for (MethodNode mn : cn.methods) {
                if (mn.name.equals(name) && mn.desc.equals(desc)) return cn.name;
            }
        }
        return "?";
    }

    private Map<MethodGraph.MethodKey, List<Link>> reverseLinks(BooleanSupplier cancelled) {
        if (reverseCache != null) return reverseCache;
        Map<MethodGraph.MethodKey, List<Link>> reverse = new HashMap<>();
        if (archive == null || archive.getClasses() == null) {
            this.reverseCache = reverse;
            return reverse;
        }
        for (ClassNode cn : new ArrayList<>(archive.getClasses().values())) {
            checkCancelled(cancelled);
            for (MethodNode mn : new ArrayList<>(cn.methods)) {
                MethodGraph.MethodKey callerKey = new MethodGraph.MethodKey(cn.name, mn.name, mn.desc);
                for (Link out : outgoing(cn.name, mn, true)) {
                    List<Link> list = reverse.get(out.targetKey());
                    if (list == null) { list = new ArrayList<>(); reverse.put(out.targetKey(), list); }
                    list.add(new Link(cn.name, mn, callerKey,
                            out.targetOwner(), out.targetNode(), out.targetKey(), out.dynamic()));
                }
            }
        }
        this.reverseCache = reverse;
        return this.reverseCache;
    }

    private static Map<MethodGraph.MethodKey, MethodGraph.MethodNode> layout(
            MethodGraph.MethodKey root, Map<MethodGraph.MethodKey, NodeDraft> drafts) {

        Map<Integer, List<NodeDraft>> layers = new LinkedHashMap<>();
        List<NodeDraft> sorted = new ArrayList<>(drafts.values());
        sorted.sort(new Comparator<NodeDraft>() {
            public int compare(NodeDraft a, NodeDraft b) {
                int r = Integer.compare(a.discovered().rank(), b.discovered().rank());
                return r != 0 ? r : a.key().symbol().compareTo(b.key().symbol());
            }
        });
        for (NodeDraft d : sorted) {
            Integer rank = d.discovered().rank();
            List<NodeDraft> layer = layers.get(rank);
            if (layer == null) { layer = new ArrayList<>(); layers.put(rank, layer); }
            layer.add(d);
        }

        Map<Integer, Float> layerWidths = new HashMap<>();
        for (Map.Entry<Integer, List<NodeDraft>> entry : layers.entrySet()) {
            float max = 0;
            for (NodeDraft d : entry.getValue()) max = Math.max(max, d.width());
            layerWidths.put(entry.getKey(), max);
        }

        int maxRank = 0, minRank = 0;
        for (int r : layers.keySet()) { maxRank = Math.max(maxRank, r); minRank = Math.min(minRank, r); }

        Map<Integer, Float> layerX = new HashMap<>();
        layerX.put(0, 0f);
        for (int r = 1; r <= maxRank; r++) {
            float prev = layerX.containsKey(r - 1) ? layerX.get(r - 1) : 0f;
            float prevW = layerWidths.containsKey(r - 1) ? layerWidths.get(r - 1) : 0f;
            layerX.put(r, prev + prevW + METHOD_GAP_X);
        }
        for (int r = -1; r >= minRank; r--) {
            float next = layerX.containsKey(r + 1) ? layerX.get(r + 1) : 0f;
            float thisW = layerWidths.containsKey(r) ? layerWidths.get(r) : 0f;
            layerX.put(r, next - thisW - METHOD_GAP_X);
        }

        Map<MethodGraph.MethodKey, MethodGraph.MethodNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<NodeDraft>> entry : layers.entrySet()) {
            List<NodeDraft> layer = entry.getValue();
            float total = 0;
            for (NodeDraft d : layer) total += d.height();
            total += METHOD_GAP_Y * Math.max(0, layer.size() - 1);
            float y = -total * 0.5f;
            float x = layerX.containsKey(entry.getKey()) ? layerX.get(entry.getKey()) : 0f;
            for (NodeDraft d : layer) {
                nodes.put(d.key(), new MethodGraph.MethodNode(
                        d.key(), d.discovered().asmNode(),
                        d.discovered().depth(), d.discovered().rank(),
                        d.key().equals(root),
                        x, y, d.width(), d.height()));
                y += d.height() + METHOD_GAP_Y;
            }
        }

        MethodGraph.MethodNode rootNode = nodes.get(root);
        if (rootNode != null) {
            float off = -rootNode.y();
            Map<MethodGraph.MethodKey, MethodGraph.MethodNode> shifted = new LinkedHashMap<>();
            for (Map.Entry<MethodGraph.MethodKey, MethodGraph.MethodNode> e : nodes.entrySet()) {
                MethodGraph.MethodNode n = e.getValue();
                shifted.put(e.getKey(), new MethodGraph.MethodNode(
                        n.key(), n.asmNode(), n.depth(), n.rank(), n.root(),
                        n.x(), n.y() + off, n.width(), n.height()));
            }
            return shifted;
        }
        return nodes;
    }

    private static MethodGraph.Bounds bounds(Map<MethodGraph.MethodKey, MethodGraph.MethodNode> nodes) {
        if (nodes.isEmpty()) return new MethodGraph.Bounds(0, 0, 1, 1);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (MethodGraph.MethodNode n : nodes.values()) {
            minX = Math.min(minX, n.x()); minY = Math.min(minY, n.y());
            maxX = Math.max(maxX, n.x() + n.width());
            maxY = Math.max(maxY, n.y() + n.height());
        }
        return new MethodGraph.Bounds(minX, minY, maxX, maxY);
    }

    private static float nodeWidth(MethodGraph.MethodKey key) {
        int chars = key.displayOwner().length() + key.name().length()
                + Math.min(40, key.descriptor().length()) + 6;
        return Math.max(METHOD_WIDTH_MIN, Math.min(METHOD_WIDTH_MAX, chars * 7.5f));
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    public static final class Request {
        private final int depth;
        private final MethodGraph.Direction direction;
        private final boolean includeExternal;

        public Request(int depth, MethodGraph.Direction direction, boolean includeExternal) {
            if (depth < INFINITE_DEPTH || depth == 0)
                throw new IllegalArgumentException("depth must be positive or INFINITE_DEPTH");
            Objects.requireNonNull(direction, "direction");
            this.depth = depth;
            this.direction       = direction;
            this.includeExternal = includeExternal;
        }

        public int depth()                    { return depth; }
        public MethodGraph.Direction direction() { return direction; }
        public boolean includeExternal()      { return includeExternal; }
    }

    private static final class Discovered {
        private final String owner;
        private final MethodNode asmNode;
        private final int depth;
        private final int rank;

        Discovered(String owner, MethodNode asmNode, int depth, int rank) {
            this.owner   = owner;
            this.asmNode = asmNode;
            this.depth   = depth;
            this.rank    = rank;
        }

        String owner()      { return owner; }
        MethodNode asmNode(){ return asmNode; }
        int depth()         { return depth; }
        int rank()          { return rank; }
    }

    private static final class NodeDraft {
        private final MethodGraph.MethodKey key;
        private final Discovered discovered;
        private final float width;
        private final float height;

        NodeDraft(MethodGraph.MethodKey key, Discovered discovered, float width, float height) {
            this.key        = key;
            this.discovered = discovered;
            this.width      = width;
            this.height     = height;
        }

        MethodGraph.MethodKey key()  { return key; }
        Discovered discovered()      { return discovered; }
        float width()                { return width; }
        float height()               { return height; }
    }

    private static final class EdgeKey {
        private final MethodGraph.MethodKey caller;
        private final MethodGraph.MethodKey callee;

        EdgeKey(MethodGraph.MethodKey caller, MethodGraph.MethodKey callee) {
            this.caller = caller; this.callee = callee;
        }

        MethodGraph.MethodKey caller() { return caller; }
        MethodGraph.MethodKey callee() { return callee; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EdgeKey)) return false;
            EdgeKey e = (EdgeKey) o;
            return caller.equals(e.caller) && callee.equals(e.callee);
        }

        @Override
        public int hashCode() { return 31 * caller.hashCode() + callee.hashCode(); }
    }

    private static final class Link {
        private final String sourceOwner;
        private final MethodNode sourceNode;
        private final MethodGraph.MethodKey sourceKey;
        private final String targetOwner;
        private final MethodNode targetNode;
        private final MethodGraph.MethodKey targetKey;
        private final boolean dynamic;

        Link(String sourceOwner, MethodNode sourceNode, MethodGraph.MethodKey sourceKey,
             String targetOwner, MethodNode targetNode, MethodGraph.MethodKey targetKey,
             boolean dynamic) {
            this.sourceOwner = sourceOwner; this.sourceNode = sourceNode; this.sourceKey = sourceKey;
            this.targetOwner = targetOwner; this.targetNode = targetNode; this.targetKey = targetKey;
            this.dynamic = dynamic;
        }

        String sourceOwner()            { return sourceOwner; }
        MethodNode sourceNode()         { return sourceNode; }
        MethodGraph.MethodKey sourceKey(){ return sourceKey; }
        String targetOwner()            { return targetOwner; }
        MethodNode targetNode()         { return targetNode; }
        MethodGraph.MethodKey targetKey(){ return targetKey; }
        boolean dynamic()               { return dynamic; }
    }

    private static final class MutableLink {
        private final String targetOwner;
        private final MethodNode targetNode;
        private final MethodGraph.MethodKey targetKey;
        private int count;
        private boolean dynamic;

        MutableLink(String targetOwner, MethodNode targetNode, MethodGraph.MethodKey targetKey) {
            this.targetOwner = targetOwner;
            this.targetNode  = targetNode;
            this.targetKey   = targetKey;
        }

        void add(boolean dynamic) { count++; this.dynamic |= dynamic; }

        Link freeze() {
            return new Link(null, null, null, targetOwner, targetNode, targetKey, dynamic);
        }
    }

    private static final class MutableEdge {
        int count;
        boolean dynamic;
        void add(boolean dynamic) { count++; this.dynamic |= dynamic; }
    }
}
