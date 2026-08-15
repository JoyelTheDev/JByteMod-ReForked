package dev.joyel.methodgraph;

import dev.joyel.theme.JBytePalette;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

final class MethodGraphCanvas extends JPanel {

    private static final float MIN_ZOOM = 0.12f;
    private static final float MAX_ZOOM = 2.4f;
    private static final float ZOOM_STEP = 1.12f;
    private static final float GRID_SIZE  = 32f;
    private static final float HEADER_H   = 42f;
    private static final float NODE_ARC   = 6f;

    private static final Color COL_BG          = new Color(0x252526);
    private static final Color COL_GRID        = new Color(0x3a3a3a);
    private static final Color COL_NODE_HEADER = new Color(0x2d2d30);
    private static final Color COL_NODE_BODY   = new Color(0x1e1e1e);
    private static final Color COL_BORDER      = new Color(0x555555);
    private static final Color COL_SELECTED    = new Color(0xffffff);
    private static final Color COL_ROOT        = new Color(0x569cd6);
    private static final Color COL_EDGE        = new Color(0x569cd6);
    private static final Color COL_EDGE_DYN    = new Color(0xdcdcaa);
    private static final Color COL_TEXT_OWNER  = new Color(0x4ec9b0);
    private static final Color COL_TEXT_METHOD = new Color(0xdcdcaa);
    private static final Color COL_TEXT_DESC   = new Color(0x808080);
    private static final Color COL_EXTERNAL    = new Color(0x808080);
    private static final Color COL_ZOOM_LABEL  = new Color(0x606060);
    private static final Color COL_MINIMAP_BG  = new Color(0x1a1a1a, false);
    private static final Color COL_MINIMAP_BOR = new Color(0x555555);
    private static final Color COL_MINIMAP_NODE = new Color(0x808080);
    private static final Color COL_MINIMAP_ROOT = new Color(0x569cd6);
    private static final Color COL_MINIMAP_SEL  = new Color(0xffffff);
    private static final Color COL_MINIMAP_VIEW = new Color(0xffffff);

    private static final int MINI_W = 160;
    private static final int MINI_H = 100;
    private static final int MINI_MARGIN = 10;

    private final Actions actions;
    private MethodGraph graph;
    private MethodGraph.MethodKey selected;
    private MethodGraph.MethodKey dragging;
    private final Map<MethodGraph.MethodKey, float[]> manualOffsets = new HashMap<>();

    private float panX, panY;
    private float zoom = 1f;
    private Point lastMouse;
    private boolean panning;
    private boolean fitRequested = true;

    MethodGraphCanvas(Actions actions) {
        this.actions = actions;
        setBackground(COL_BG);
        setFocusable(true);
        InputHandler handler = new InputHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
        addKeyListener(handler);
    }

    void setGraph(MethodGraph graph) {
        this.graph = graph;
        this.manualOffsets.keySet().retainAll(graph.nodes().keySet());
        if (selected != null && !graph.nodes().containsKey(selected)) selected = null;
        fitRequested = true;
        repaint();
    }

    void requestFit() {
        fitRequested = true;
        repaint();
    }

    void resetLayout() {
        manualOffsets.clear();
        fitRequested = true;
        repaint();
    }

    void centerRoot() {
        if (graph != null) centerOn(graph.root());
    }

    void selectAndCenter(MethodGraph.MethodKey key) {
        if (graph == null || !graph.nodes().containsKey(key)) return;
        selected = key;
        centerOn(key);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (fitRequested) { fitGraph(); fitRequested = false; }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        drawGrid(g2);
        if (graph == null) { g2.dispose(); return; }

        Set<MethodGraph.MethodKey> hood = selected == null ? Set.of() : neighborhood(selected);
        drawEdges(g2, hood);
        List<MethodGraph.MethodNode> nodes = new ArrayList<>(graph.nodes().values());
        nodes.sort(Comparator.comparing(n -> n.key().symbol()));
        for (MethodGraph.MethodNode n : nodes) drawNode(g2, n, hood);
        drawMiniMap(g2);
        drawZoomLabel(g2);
        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(COL_BG);
        g2.fillRect(0, 0, getWidth(), getHeight());
        float spacing = GRID_SIZE * zoom;
        while (spacing < 12f) spacing *= 2f;
        g2.setColor(COL_GRID);
        g2.setStroke(new BasicStroke(0.5f));
        float startX = modulo(panX, spacing);
        float startY = modulo(panY, spacing);
        for (float x = startX; x < getWidth(); x += spacing)
            g2.draw(new Line2D.Float(x, 0, x, getHeight()));
        for (float y = startY; y < getHeight(); y += spacing)
            g2.draw(new Line2D.Float(0, y, getWidth(), y));
    }

    private void drawEdges(Graphics2D g2, Set<MethodGraph.MethodKey> hood) {
        for (MethodGraph.CallEdge edge : graph.calls()) {
            MethodGraph.MethodNode caller = graph.nodes().get(edge.caller());
            MethodGraph.MethodNode callee = graph.nodes().get(edge.callee());
            if (caller == null || callee == null) continue;
            boolean active = selected == null
                    || edge.caller().equals(selected)
                    || edge.callee().equals(selected);
            Color base = edge.dynamicDispatch() ? COL_EDGE_DYN : COL_EDGE;
            Color col = withAlpha(base, active ? 180 : 35);
            float[] fromOff = offset(edge.caller());
            float[] toOff   = offset(edge.callee());
            float x1 = sx(caller.x() + fromOff[0] + caller.width());
            float y1 = sy(caller.y() + fromOff[1] + caller.height() * 0.5f);
            float x2 = sx(callee.x() + toOff[0]);
            float y2 = sy(callee.y() + toOff[1] + callee.height() * 0.5f);
            boolean goRight = x2 >= x1;
            if (!goRight) { float tx = x1; x1 = sx(caller.x() + fromOff[0]); x2 = sx(callee.x() + toOff[0] + callee.width()); float tmp = x1; x1 = tmp; }
            float bend = Math.max(40f, Math.abs(x2 - x1) * 0.4f);
            float cx1 = x1 + bend, cx2 = x2 - bend;
            Path2D path = new Path2D.Float();
            path.moveTo(x1, y1);
            path.curveTo(cx1, y1, cx2, y2, x2, y2);
            g2.setColor(col);
            g2.setStroke(new BasicStroke(active ? 1.6f : 0.9f));
            g2.draw(path);
            drawArrow(g2, cx2, y2, x2, y2, col, active ? 7f : 5f);
            if (active && edge.callSites() > 1 && zoom > 0.5f) {
                g2.setColor(withAlpha(COL_TEXT_DESC, 200));
                g2.setFont(scaledFont(g2, 11f));
                g2.drawString("x" + edge.callSites(), (x1 + x2) * 0.5f + 3, (y1 + y2) * 0.5f - 6);
            }
        }
    }

    private void drawNode(Graphics2D g2, MethodGraph.MethodNode node, Set<MethodGraph.MethodKey> hood) {
        float[] off = offset(node.key());
        float left  = sx(node.x() + off[0]);
        float top   = sy(node.y() + off[1]);
        float w     = node.width()  * zoom;
        float h     = node.height() * zoom;
        float hh    = HEADER_H * zoom;

        if (!new Rectangle2D.Float(left, top, w, h).intersects(0, 0, getWidth(), getHeight())) return;

        boolean active   = selected == null || hood.contains(node.key());
        boolean isSel    = node.key().equals(selected);
        int alpha = active ? 255 : 70;

        g2.setColor(withAlpha(COL_NODE_BODY, alpha));
        g2.fill(new RoundRectangle2D.Float(left, top, w, h, NODE_ARC, NODE_ARC));
        g2.setColor(withAlpha(COL_NODE_HEADER, alpha));
        g2.fill(new RoundRectangle2D.Float(left, top, w, hh, NODE_ARC, NODE_ARC));
        g2.fill(new Rectangle2D.Float(left, top + hh * 0.5f, w, hh * 0.5f));

        Color border = isSel ? COL_SELECTED
                : withAlpha(COL_BORDER, active ? 160 : 45);
        g2.setColor(border);
        g2.setStroke(new BasicStroke(isSel ? 1.8f : 1f));
        g2.draw(new RoundRectangle2D.Float(left, top, w, h, NODE_ARC, NODE_ARC));

        if (node.root()) {
            g2.setColor(withAlpha(COL_ROOT, active ? 230 : 70));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Line2D.Float(left + 1, top + 4, left + 1, top + hh - 4));
        }

        if (zoom >= 0.28f) drawNodeHeader(g2, node, left, top, w, hh, active, alpha);
    }

    private void drawNodeHeader(Graphics2D g2, MethodGraph.MethodNode node,
                                float left, float top, float w, float hh,
                                boolean active, int alpha) {
        float tx = left + 10f * zoom;
        float ty = top  +  7f * zoom;

        String owner = node.key().displayOwner();
        g2.setFont(scaledFont(g2, 11.5f));
        g2.setColor(withAlpha(COL_TEXT_OWNER, alpha));
        g2.drawString(owner, tx, ty + g2.getFontMetrics().getAscent());

        float rowH = g2.getFontMetrics().getHeight() + 2f * zoom;
        String mName = node.key().name();
        g2.setFont(scaledFontBold(g2, 11.5f));
        g2.setColor(withAlpha(COL_TEXT_METHOD, alpha));
        g2.drawString(mName, tx, ty + rowH + g2.getFontMetrics().getAscent());

        String desc = compactDesc(node.key().descriptor());
        g2.setFont(scaledFont(g2, 10.5f));
        g2.setColor(withAlpha(COL_TEXT_DESC, active ? 200 : 65));
        float descX = tx + g2.getFontMetrics(scaledFontBold(g2, 11.5f)).stringWidth(mName);
        g2.drawString(desc, descX, ty + rowH + g2.getFontMetrics().getAscent());

        if (node.external()) {
            String ext = "external";
            g2.setFont(scaledFont(g2, 10f));
            g2.setColor(withAlpha(COL_EXTERNAL, alpha));
            int sw = g2.getFontMetrics().stringWidth(ext);
            g2.drawString(ext, left + w - sw - 8f * zoom, ty + g2.getFontMetrics().getAscent());
        } else if (node.root()) {
            String r = "ROOT";
            g2.setFont(scaledFontBold(g2, 10f));
            g2.setColor(withAlpha(COL_ROOT, alpha));
            int sw = g2.getFontMetrics().stringWidth(r);
            g2.drawString(r, left + w - sw - 8f * zoom, ty + g2.getFontMetrics().getAscent());
        }
    }

    private void drawMiniMap(Graphics2D g2) {
        if (graph == null) return;
        float[] bounds = currentBounds();
        float bw = Math.max(1, bounds[2] - bounds[0]);
        float bh = Math.max(1, bounds[3] - bounds[1]);
        float pad = 6f;
        float scale = Math.min((MINI_W - pad * 2) / bw, (MINI_H - pad * 2) / bh);
        float ml = getWidth()  - MINI_W - MINI_MARGIN;
        float mt = getHeight() - MINI_H - MINI_MARGIN;
        float gLeft = ml + (MINI_W - bw * scale) * 0.5f;
        float gTop  = mt + (MINI_H - bh * scale) * 0.5f;

        g2.setColor(COL_MINIMAP_BG);
        g2.fill(new RoundRectangle2D.Float(ml, mt, MINI_W, MINI_H, 4, 4));
        g2.setColor(COL_MINIMAP_BOR);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(ml, mt, MINI_W, MINI_H, 4, 4));

        for (MethodGraph.MethodNode n : graph.nodes().values()) {
            float[] off = offset(n.key());
            float nx = gLeft + (n.x() + off[0] - bounds[0]) * scale;
            float ny = gTop  + (n.y() + off[1] - bounds[1]) * scale;
            float nw = Math.max(1f, n.width()  * scale);
            float nh = Math.max(1f, n.height() * scale);
            Color nc = n.key().equals(selected) ? COL_MINIMAP_SEL
                    : n.root() ? COL_MINIMAP_ROOT : COL_MINIMAP_NODE;
            g2.setColor(nc);
            g2.fill(new Rectangle2D.Float(nx, ny, nw, nh));
        }

        float vx1 = gLeft + (-panX / zoom - bounds[0]) * scale;
        float vy1 = gTop  + (-panY / zoom - bounds[1]) * scale;
        float vx2 = gLeft + ((getWidth()  - panX) / zoom - bounds[0]) * scale;
        float vy2 = gTop  + ((getHeight() - panY) / zoom - bounds[1]) * scale;
        g2.setColor(withAlpha(COL_MINIMAP_VIEW, 140));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Rectangle2D.Float(vx1, vy1, vx2 - vx1, vy2 - vy1));
    }

    private void drawZoomLabel(Graphics2D g2) {
        String label = Math.round(zoom * 100f) + "%";
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g2.setColor(COL_ZOOM_LABEL);
        g2.drawString(label, 9, getHeight() - 9);
    }

    private void drawArrow(Graphics2D g2, float fromX, float fromY,
                           float toX, float toY, Color col, float size) {
        float dx = toX - fromX, dy = toY - fromY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        dx /= len; dy /= len;
        float px = -dy, py = dx;
        float bx = toX - dx * size, by = toY - dy * size;
        int[] xs = { Math.round(toX), Math.round(bx + px * size * 0.55f), Math.round(bx - px * size * 0.55f) };
        int[] ys = { Math.round(toY), Math.round(by + py * size * 0.55f), Math.round(by - py * size * 0.55f) };
        g2.setColor(col);
        g2.fillPolygon(xs, ys, 3);
    }

    private MethodGraph.MethodNode nodeAt(int mx, int my) {
        if (graph == null) return null;
        for (MethodGraph.MethodNode n : graph.nodes().values()) {
            float[] off = offset(n.key());
            float left = sx(n.x() + off[0]), top = sy(n.y() + off[1]);
            if (mx >= left && mx <= left + n.width() * zoom
                    && my >= top && my <= top + n.height() * zoom) return n;
        }
        return null;
    }

    private boolean inMiniMap(int mx, int my) {
        int ml = getWidth()  - MINI_W - MINI_MARGIN;
        int mt = getHeight() - MINI_H - MINI_MARGIN;
        return mx >= ml && mx <= ml + MINI_W && my >= mt && my <= mt + MINI_H;
    }

    private void handleMiniMapClick(int mx, int my) {
        if (graph == null) return;
        float[] bounds = currentBounds();
        float bw = Math.max(1, bounds[2] - bounds[0]);
        float bh = Math.max(1, bounds[3] - bounds[1]);
        float scale = Math.min((MINI_W - 12f) / bw, (MINI_H - 12f) / bh);
        float ml  = getWidth()  - MINI_W - MINI_MARGIN;
        float mt  = getHeight() - MINI_H - MINI_MARGIN;
        float gLeft = ml + (MINI_W - bw * scale) * 0.5f;
        float gTop  = mt + (MINI_H - bh * scale) * 0.5f;
        float wX = bounds[0] + (mx - gLeft) / scale;
        float wY = bounds[1] + (my - gTop)  / scale;
        panX = getWidth()  * 0.5f - wX * zoom;
        panY = getHeight() * 0.5f - wY * zoom;
        repaint();
    }

    private void fitGraph() {
        if (graph == null) return;
        float[] b = currentBounds();
        float w = Math.max(1, b[2] - b[0]);
        float h = Math.max(1, b[3] - b[1]);
        zoom = Math.max(MIN_ZOOM, Math.min(1f,
                Math.min((getWidth() - 80f) / w, (getHeight() - 80f) / h)));
        panX = getWidth()  * 0.5f - (b[0] + w * 0.5f) * zoom;
        panY = getHeight() * 0.5f - (b[1] + h * 0.5f) * zoom;
    }

    private void centerOn(MethodGraph.MethodKey key) {
        MethodGraph.MethodNode n = graph == null ? null : graph.nodes().get(key);
        if (n == null) return;
        float[] off = offset(key);
        float cx = n.x() + off[0] + n.width()  * 0.5f;
        float cy = n.y() + off[1] + n.height() * 0.5f;
        panX = getWidth()  * 0.5f - cx * zoom;
        panY = getHeight() * 0.5f - cy * zoom;
        repaint();
    }

    private float[] currentBounds() {
        if (graph == null) return new float[]{0,0,1,1};
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (MethodGraph.MethodNode n : graph.nodes().values()) {
            float[] off = offset(n.key());
            minX = Math.min(minX, n.x() + off[0]);
            minY = Math.min(minY, n.y() + off[1]);
            maxX = Math.max(maxX, n.x() + off[0] + n.width());
            maxY = Math.max(maxY, n.y() + off[1] + n.height());
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    private Set<MethodGraph.MethodKey> neighborhood(MethodGraph.MethodKey key) {
        Set<MethodGraph.MethodKey> result = new HashSet<>();
        result.add(key);
        for (MethodGraph.CallEdge e : graph.calls()) {
            if (e.caller().equals(key)) result.add(e.callee());
            if (e.callee().equals(key)) result.add(e.caller());
        }
        return result;
    }

    private float[] offset(MethodGraph.MethodKey key) {
        return manualOffsets.getOrDefault(key, new float[]{0, 0});
    }

    private float sx(float wx) { return panX + wx * zoom; }
    private float sy(float wy) { return panY + wy * zoom; }

    private static Font scaledFont(Graphics2D g2, float size) {
        return g2.getFont().deriveFont(Font.PLAIN, size);
    }
    private static Font scaledFontBold(Graphics2D g2, float size) {
        return g2.getFont().deriveFont(Font.BOLD, size);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static float modulo(float v, float m) {
        float r = v % m; return r < 0 ? r + m : r;
    }

    private static String compactDesc(String d) {
        return d.length() <= 42 ? d : d.substring(0, 39) + "...";
    }

    interface Actions {
        void onDoubleClick(MethodGraph.MethodNode node);
        void showContextMenu(MethodGraph.MethodNode node, Component parent, int x, int y);
    }

    private final class InputHandler extends MouseAdapter implements MouseMotionListener, MouseWheelListener, KeyListener {

        private boolean miniDragging;

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (graph == null) return;
            if (e.getButton() == MouseEvent.BUTTON1) {
                if (inMiniMap(e.getX(), e.getY())) {
                    miniDragging = true;
                    handleMiniMapClick(e.getX(), e.getY());
                    return;
                }
                MethodGraph.MethodNode hit = nodeAt(e.getX(), e.getY());
                if (hit != null) {
                    selected = hit.key();
                    dragging = hit.key();
                } else {
                    selected = null;
                    dragging = null;
                }
                lastMouse = e.getPoint();
                panning = (hit == null);
                repaint();
            } else if (e.getButton() == MouseEvent.BUTTON2) {
                lastMouse = e.getPoint();
                panning = true;
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                MethodGraph.MethodNode hit = nodeAt(e.getX(), e.getY());
                if (hit != null) {
                    selected = hit.key();
                    repaint();
                    actions.showContextMenu(hit, MethodGraphCanvas.this, e.getX(), e.getY());
                }
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            panning = false;
            dragging = null;
            miniDragging = false;
            lastMouse = null;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 && graph != null) {
                MethodGraph.MethodNode hit = nodeAt(e.getX(), e.getY());
                if (hit != null) actions.onDoubleClick(hit);
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (lastMouse == null) { lastMouse = e.getPoint(); return; }
            int dx = e.getX() - lastMouse.x;
            int dy = e.getY() - lastMouse.y;
            lastMouse = e.getPoint();
            if (miniDragging) { handleMiniMapClick(e.getX(), e.getY()); return; }
            if (panning) {
                panX += dx; panY += dy; repaint();
            } else if (dragging != null) {
                float[] off = manualOffsets.computeIfAbsent(dragging, k -> new float[]{0, 0});
                off[0] += dx / zoom;
                off[1] += dy / zoom;
                repaint();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            if (graph == null) return;
            MethodGraph.MethodNode hit = nodeAt(e.getX(), e.getY());
            setCursor(hit != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            float mx = e.getX(), my = e.getY();
            float wx = (mx - panX) / zoom, wy = (my - panY) / zoom;
            float factor = e.getWheelRotation() < 0 ? ZOOM_STEP : 1f / ZOOM_STEP;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
            panX = mx - wx * zoom;
            panY = my - wy * zoom;
            repaint();
        }

        @Override public void keyTyped(KeyEvent e) {}
        @Override public void keyReleased(KeyEvent e) {}

        @Override
        public void keyPressed(KeyEvent e) {
            if (graph == null) return;
            if (e.getKeyCode() == KeyEvent.VK_F) { requestFit(); return; }
            if (e.getKeyCode() == KeyEvent.VK_HOME) { centerRoot(); return; }
            if (e.getKeyCode() == KeyEvent.VK_R) { resetLayout(); return; }

            int hor = e.getKeyCode() == KeyEvent.VK_LEFT ? -1
                    : e.getKeyCode() == KeyEvent.VK_RIGHT ? 1 : 0;
            int ver = e.getKeyCode() == KeyEvent.VK_UP ? -1
                    : e.getKeyCode() == KeyEvent.VK_DOWN ? 1 : 0;
            if (hor == 0 && ver == 0) return;

            MethodGraph.MethodNode cur = selected == null
                    ? graph.nodes().get(graph.root())
                    : graph.nodes().get(selected);
            if (cur == null) return;
            float[] coff = offset(cur.key());
            float cx = cur.x() + coff[0] + cur.width()  * 0.5f;
            float cy = cur.y() + coff[1] + cur.height() * 0.5f;
            MethodGraph.MethodNode best = null;
            float bestScore = Float.MAX_VALUE;
            for (MethodGraph.MethodNode candidate : graph.nodes().values()) {
                if (candidate == cur) continue;
                float[] boff = offset(candidate.key());
                float dx = candidate.x() + boff[0] + candidate.width()  * 0.5f - cx;
                float dy = candidate.y() + boff[1] + candidate.height() * 0.5f - cy;
                if (hor != 0 && Math.signum(dx) != hor) continue;
                if (ver != 0 && Math.signum(dy) != ver) continue;
                float score = (hor != 0 ? Math.abs(dx) : Math.abs(dy))
                        + (hor != 0 ? Math.abs(dy) : Math.abs(dx)) * 0.38f;
                if (score < bestScore) { bestScore = score; best = candidate; }
            }
            if (best != null) { selected = best.key(); centerOn(best.key()); }
        }
    }
}
