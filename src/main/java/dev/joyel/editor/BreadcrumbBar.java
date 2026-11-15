package dev.joyel.editor;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.ui.ToastManager;
import me.grax.jbytemod.JarArchive;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BreadcrumbBar extends JComponent {

    private enum Kind {
        PACKAGE, CLASS, METHOD, HINT
    }

    private record Segment(String text, Kind kind, Runnable action, String tooltip) {
    }

    private static final int HEIGHT = 28;
    private static final int CHIP_PADDING = 7;
    private static final int DOT_WIDTH = 14;
    private static final int SEPARATOR_WIDTH = 16;
    private static final int MARGIN = 8;
    private static final String ELLIPSIS = "\u2026";
    private static final String SEPARATOR = "\u203a";
    private static final String HINT_TEXT = "Select a class or method to see its path";

    private final JByteMod jbm;
    private List<Segment> segments = new ArrayList<>();
    private String fullPath = "";
    private final List<Segment> shown = new ArrayList<>();
    private final List<Rectangle> shownBounds = new ArrayList<>();
    private int hover = -1;

    public BreadcrumbBar(JByteMod jbm) {
        this.jbm = jbm;
        setPreferredSize(new Dimension(10, HEIGHT));
        setMinimumSize(new Dimension(0, HEIGHT));
        ToolTipManager.sharedInstance().registerComponent(this);
        showHint();

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = indexAt(e.getPoint());
                if (index != hover) {
                    hover = index;
                    boolean clickable = index >= 0 && shown.get(index).action() != null;
                    setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    copyPath();
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int index = indexAt(e.getPoint());
                if (index >= 0 && shown.get(index).action() != null) {
                    shown.get(index).action().run();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setSelection(ClassNode classNode, MethodNode methodNode) {
        if (classNode == null) {
            showHint();
            return;
        }
        List<Segment> built = new ArrayList<>();
        String[] parts = classNode.name.split("/");
        StringBuilder pathBuilder = new StringBuilder();

        for (int i = 0; i < parts.length - 1; i++) {
            final int depth = i;
            if (i > 0) {
                pathBuilder.append('.');
            }
            pathBuilder.append(parts[i]);
            built.add(new Segment(parts[i], Kind.PACKAGE,
                    () -> TreeNavigator.selectPackage(jbm.getJarTree(), parts, depth), pathBuilder.toString()));
        }

        String packagePrefix = parts.length > 1
                ? String.join("/", Arrays.copyOf(parts, parts.length - 1)) + "/"
                : "";
        String simple = parts[parts.length - 1];
        String[] chain = splitNested(simple);
        StringBuilder owner = new StringBuilder(packagePrefix);
        String dottedBase = pathBuilder.length() > 0 ? pathBuilder + "." : "";
        StringBuilder dotted = new StringBuilder(dottedBase);
        for (int j = 0; j < chain.length; j++) {
            if (j > 0) {
                owner.append('$');
                dotted.append('$');
            }
            owner.append(chain[j]);
            dotted.append(chain[j]);
            final String ownerName = owner.toString();
            boolean last = j == chain.length - 1 && methodNode == null;
            built.add(new Segment(chain[j], Kind.CLASS,
                    last ? null : () -> selectClassByName(ownerName), dotted.toString()));
        }

        String path = dotted.toString();
        if (methodNode != null) {
            built.add(new Segment(methodLabel(methodNode), Kind.METHOD, null,
                    classNode.name + "." + methodNode.name + methodNode.desc));
            path = path + "." + methodNode.name;
        }
        segments = built;
        fullPath = path;
        hover = -1;
        repaint();
    }

    private void showHint() {
        segments = new ArrayList<>();
        segments.add(new Segment(HINT_TEXT, Kind.HINT, null, null));
        fullPath = "";
        hover = -1;
        repaint();
    }

    private static String[] splitNested(String simple) {
        if (simple.indexOf('$') <= 0) {
            return new String[]{simple};
        }
        String[] chain = simple.split("\\$", -1);
        for (String item : chain) {
            if (item.isEmpty()) {
                return new String[]{simple};
            }
        }
        return chain;
    }

    private static String methodLabel(MethodNode method) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        StringBuilder sb = new StringBuilder(method.name).append('(');
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = arguments[i].getClassName();
            int dot = name.lastIndexOf('.');
            sb.append(dot >= 0 ? name.substring(dot + 1) : name);
        }
        return sb.append(')').toString();
    }

    private void selectClassByName(String internalName) {
        JarArchive archive = jbm.getJarArchive();
        if (archive == null || archive.getClasses() == null) {
            return;
        }
        ClassNode target = archive.getClasses().get(internalName);
        if (target != null) {
            TreeNavigator.selectClass(jbm.getJarTree(), target);
        }
    }

    private void copyPath() {
        if (fullPath.isEmpty()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(fullPath), null);
        ToastManager.info("Copied " + fullPath);
    }

    private int indexAt(Point point) {
        for (int i = 0; i < shownBounds.size(); i++) {
            if (shownBounds.get(i).contains(point)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = indexAt(event.getPoint());
        if (index < 0) {
            return null;
        }
        return shown.get(index).tooltip();
    }

    private static Color dotColor(Kind kind) {
        return switch (kind) {
            case PACKAGE -> new Color(0xF59E0B);
            case CLASS -> new Color(0x2DD4BF);
            case METHOD -> new Color(0xA78BFA);
            default -> Color.GRAY;
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        Color panel = UIManager.getColor("Panel.background");
        Color label = UIManager.getColor("Label.foreground");
        Color background = panel != null ? panel : new Color(0x2B2B2B);
        Color foreground = label != null ? label : new Color(0xDDDDDD);
        Color muted = EditorExtras.mix(foreground, background, 0.4f);

        g2.setColor(EditorExtras.mix(background, foreground, 0.035f));
        g2.fillRect(0, 0, width, height);
        g2.setColor(EditorExtras.withAlpha(foreground, 40));
        g2.drawLine(0, height - 1, width, height - 1);

        Font base = getFont() != null ? getFont() : UIManager.getFont("Label.font");
        Font regular = base.deriveFont(Font.PLAIN, 12f);
        Font bold = base.deriveFont(Font.BOLD, 12f);
        FontMetrics regularMetrics = g2.getFontMetrics(regular);
        FontMetrics boldMetrics = g2.getFontMetrics(bold);

        int count = segments.size();
        int[] widths = new int[count];
        for (int i = 0; i < count; i++) {
            Segment s = segments.get(i);
            FontMetrics fm = i == count - 1 && s.kind() != Kind.HINT ? boldMetrics : regularMetrics;
            widths[i] = fm.stringWidth(s.text()) + CHIP_PADDING * 2 + (s.kind() == Kind.HINT ? 0 : DOT_WIDTH);
        }
        int ellipsisWidth = regularMetrics.stringWidth(ELLIPSIS) + CHIP_PADDING * 2;
        int available = width - MARGIN * 2;

        int start = 0;
        while (start < count - 1 && widthFrom(widths, start, count) + (start > 0 ? ellipsisWidth + SEPARATOR_WIDTH : 0) > available) {
            start++;
        }

        shown.clear();
        shownBounds.clear();
        int x = MARGIN;
        int chipY = 3;
        int chipHeight = height - 7;
        int baseline = (height - 1 + regularMetrics.getAscent() - regularMetrics.getDescent()) / 2;

        if (start > 0) {
            Rectangle bounds = new Rectangle(x, chipY, ellipsisWidth, chipHeight);
            Segment collapsed = new Segment(ELLIPSIS, Kind.HINT, null, fullPath);
            shown.add(collapsed);
            shownBounds.add(bounds);
            g2.setFont(regular);
            g2.setColor(muted);
            g2.drawString(ELLIPSIS, x + CHIP_PADDING, baseline);
            x += ellipsisWidth;
            drawSeparator(g2, x, baseline, muted);
            x += SEPARATOR_WIDTH;
        }

        for (int i = start; i < count; i++) {
            Segment segment = segments.get(i);
            boolean last = i == count - 1;
            boolean emphasized = last && segment.kind() != Kind.HINT;
            Rectangle bounds = new Rectangle(x, chipY, widths[i], chipHeight);
            int shownIndex = shown.size();
            shown.add(segment);
            shownBounds.add(bounds);

            boolean clickable = segment.action() != null;
            if (hover == shownIndex && clickable) {
                g2.setColor(EditorExtras.withAlpha(foreground, 30));
                g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
            }

            int textX = x + CHIP_PADDING;
            if (segment.kind() != Kind.HINT) {
                g2.setColor(dotColor(segment.kind()));
                g2.fillOval(textX, height / 2 - 4, 8, 8);
                textX += DOT_WIDTH;
            }
            g2.setFont(emphasized ? bold : regular);
            g2.setColor(emphasized || (hover == shownIndex && clickable) ? foreground : muted);
            g2.drawString(segment.text(), textX, baseline);
            x += widths[i];
            if (!last) {
                drawSeparator(g2, x, baseline, muted);
                x += SEPARATOR_WIDTH;
            }
        }
        g2.dispose();
    }

    private static int widthFrom(int[] widths, int start, int count) {
        int total = 0;
        for (int i = start; i < count; i++) {
            total += widths[i];
        }
        return total + SEPARATOR_WIDTH * (count - 1 - start);
    }

    private static void drawSeparator(Graphics2D g2, int x, int baseline, Color color) {
        g2.setColor(EditorExtras.withAlpha(color, 170));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(SEPARATOR, x + (SEPARATOR_WIDTH - fm.stringWidth(SEPARATOR)) / 2, baseline);
    }
}
