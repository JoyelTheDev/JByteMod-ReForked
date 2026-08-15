package dev.joyel.methodgraph;

import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class MethodGraphAnalyzer {

    public static final int INFINITE_DEPTH = -1;

    private static final float METHOD_WIDTH_MIN = 210f;
    private static final float METHOD_WIDTH_MAX = 520f;
    private static final float METHOD_HEIGHT   = 60f;
    private static final float METHOD_GAP_X    = 200f;
    private static final float METHOD_GAP_Y    = 90f;

    private final JarArchive archive;
    private Map<MethodKey, List<Link>> reverseCache;

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
                for (Link link : reverseLinks(cancelled).getOrDefault(current, List.of())) {
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
                edges.computeIfAbsent(new EdgeKey(entry.getKey(), link.targetKey()),
                        ignored -> new MutableEdge()).add(link.dynamic());
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
        List<MethodGraph.CallEdge> calls = edges.entrySet().stream()
                .map(e -> new MethodGraph.CallEdge(e.getKey().caller(), e.getKey().callee(),
                        e.getValue().count, e.getValue().dynamic))
                .sorted(Comparator.comparing(c -> c.caller().symbol()))
                .toList();
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

    private final Map<MethodGraph.MethodKey, List<Link>> outgoingCache = new HashMap<>();

    private List<Link> outgoing(String owner, MethodNode method, boolean includeExternal) {
        MethodGraph.MethodKey k = new MethodGraph.MethodKey(owner, method.name, method.desc);
        List<Link> all = outgoingCache.computeIfAbsent(k, ignored -> scanOutgoing(owner, method));
        return includeExternal ? all : all.stream().filter(l -> l.targetNode() != null).toList();
    }

    private List<Link> scanOutgoing(String callerOwner, MethodNode method) {
        Map<MethodGraph.MethodKey, MutableLink> links = new LinkedHashMap<>();
        if (method.instructions == null) return List.of();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode m) {
                addMethodLink(m.owner, m.name, m.desc, false, links);
            } else if (insn instanceof InvokeDynamicInsnNode dyn) {
                for (Object arg : dyn.bsmArgs) {
                    if (arg instanceof Handle h && h.getDesc().startsWith("(")) {
                        addMethodLink(h.getOwner(), h.getName(), h.getDesc(), true, links);
                    }
                }
            }
        }
        return links.values().stream().map(MutableLink::freeze).toList();
    }

    private void addMethodLink(String owner, String name, String desc, boolean dynamic,
                               Map<MethodGraph.MethodKey, MutableLink> links) {
        MethodGraph.MethodKey key = new MethodGraph.MethodKey(owner, name, desc);
        MethodNode resolved = resolveMethod(owner, name, desc);
        String resolvedOwner = resolved != null ? findOwner(name, desc) : owner;
        links.computeIfAbsent(key, ignored -> new MutableLink(resolvedOwner, resolved, key))
                .add(dynamic);
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
        for (ClassNode cn : List.copyOf(archive.getClasses().values())) {
            checkCancelled(cancelled);
            for (MethodNode mn : List.copyOf(cn.methods)) {
                MethodGraph.MethodKey callerKey = new MethodGraph.MethodKey(cn.name, mn.name, mn.desc);
                for (Link out : outgoing(cn.name, mn, true)) {
                    reverse.computeIfAbsent(out.targetKey(), ignored -> new ArrayList<>())
                            .add(new Link(cn.name, mn, callerKey, out.targetOwner(),
                                    out.targetNode(), out.targetKey(), out.dynamic()));
                }
            }
        }
        reverse.replaceAll((k, v) -> List.copyOf(v));
        this.reverseCache = Map.copyOf(reverse);
        return this.reverseCache;
    }

    private static Map<MethodGraph.MethodKey, MethodGraph.MethodNode> layout(
            MethodGraph.MethodKey root, Map<MethodGraph.MethodKey, NodeDraft> drafts) {

        Map<Integer, List<NodeDraft>> layers = new LinkedHashMap<>();
        drafts.values().stream()
                .sorted(Comparator.comparingInt((NodeDraft d) -> d.discovered().rank())
                        .thenComparing(d -> d.key().symbol()))
                .forEach(d -> layers.computeIfAbsent(d.discovered().rank(),
                        ignored -> new ArrayList<>()).add(d));

        Map<Integer, Float> layerWidths = new HashMap<>();
        layers.forEach((rank, layer) ->
                layerWidths.put(rank, (float) layer.stream()
                        .mapToDouble(NodeDraft::width).max().orElse(0)));

        Map<Integer, Float> layerX = new HashMap<>();
        layerX.put(0, 0f);
        int maxRank = layers.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int minRank = layers.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
        for (int r = 1; r <= maxRank; r++) {
            layerX.put(r, layerX.getOrDefault(r - 1, 0f) + layerWidths.getOrDefault(r - 1, 0f) + METHOD_GAP_X);
        }
        for (int r = -1; r >= minRank; r--) {
            layerX.put(r, layerX.getOrDefault(r + 1, 0f) - layerWidths.getOrDefault(r, 0f) - METHOD_GAP_X);
        }

        Map<MethodGraph.MethodKey, MethodGraph.MethodNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<NodeDraft>> entry : layers.entrySet()) {
            List<NodeDraft> layer = entry.getValue();
            float total = (float) layer.stream().mapToDouble(NodeDraft::height).sum()
                    + METHOD_GAP_Y * Math.max(0, layer.size() - 1);
            float y = -total * 0.5f;
            for (NodeDraft d : layer) {
                float x = layerX.getOrDefault(entry.getKey(), 0f);
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
            nodes.forEach((k, n) -> shifted.put(k, new MethodGraph.MethodNode(
                    n.key(), n.asmNode(), n.depth(), n.rank(), n.root(),
                    n.x(), n.y() + off, n.width(), n.height())));
            return Map.copyOf(shifted);
        }
        return Map.copyOf(nodes);
    }

    private static MethodGraph.Bounds bounds(Map<MethodGraph.MethodKey, MethodGraph.MethodNode> nodes) {
        if (nodes.isEmpty()) return new MethodGraph.Bounds(0, 0, 1, 1);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (MethodGraph.MethodNode n : nodes.values()) {
            minX = Math.min(minX, n.x());
            minY = Math.min(minY, n.y());
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

    public record Request(int depth, MethodGraph.Direction direction, boolean includeExternal) {
        public Request {
            if (depth < INFINITE_DEPTH || depth == 0)
                throw new IllegalArgumentException("depth must be positive or INFINITE_DEPTH");
            Objects.requireNonNull(direction);
        }
    }

    private record MethodKey(String owner, String name, String desc) {}

    private record Discovered(String owner, MethodNode asmNode, int depth, int rank) {}

    private record NodeDraft(MethodGraph.MethodKey key, Discovered discovered,
                             float width, float height) {}

    private record EdgeKey(MethodGraph.MethodKey caller, MethodGraph.MethodKey callee) {}

    private record Link(String sourceOwner, MethodNode sourceNode, MethodGraph.MethodKey sourceKey,
                        String targetOwner, MethodNode targetNode, MethodGraph.MethodKey targetKey,
                        boolean dynamic) {}

    private static final class MutableLink {
        private final String targetOwner;
        private final MethodNode targetNode;
        private final MethodGraph.MethodKey targetKey;
        private int count;
        private boolean dynamic;

        MutableLink(String targetOwner, MethodNode targetNode, MethodGraph.MethodKey targetKey) {
            this.targetOwner = targetOwner;
            this.targetNode = targetNode;
            this.targetKey = targetKey;
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
