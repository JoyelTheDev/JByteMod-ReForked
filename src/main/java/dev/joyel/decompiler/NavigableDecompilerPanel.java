package dev.joyel.decompiler;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.ui.DecompilerPanel;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NavigableDecompilerPanel extends DecompilerPanel {

    private final JByteMod jbm;
    private List<NavigableToken> tokens = new ArrayList<>();
    private NavigableToken hoveredToken = null;
    private String currentClassName = null;

    public NavigableDecompilerPanel(JByteMod jbm) {
        super();
        this.jbm = jbm;

        Handler handler = new Handler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    public void loadTokens(String source, String className) {
        this.currentClassName = className;
        JarArchive archive = jbm.getJarArchive();
        if (archive == null) {
            tokens = new ArrayList<>();
            return;
        }
        DecompilerTokenParser parser = new DecompilerTokenParser(archive);
        tokens = parser.parse(source, className);
        hoveredToken = null;
        repaint();
    }

    public void clearTokens() {
        tokens = new ArrayList<>();
        hoveredToken = null;
        currentClassName = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (tokens.isEmpty() || hoveredToken == null) return;
        paintUnderline(g, hoveredToken);
    }

    private void paintUnderline(Graphics g, NavigableToken token) {
        try {
            Rectangle startRect = modelToView(token.startOffset());
            Rectangle endRect   = modelToView(token.endOffset());
            if (startRect == null || endRect == null) return;

            int y    = startRect.y + startRect.height - 1;
            int x1   = startRect.x;
            int x2   = (startRect.y == endRect.y) ? endRect.x : startRect.x + getWidth();

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(resolveUnderlineColor(token));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(x1, y, x2, y);
            g2.dispose();
        } catch (BadLocationException ignored) {}
    }

    private Color resolveUnderlineColor(NavigableToken token) {
        switch (token.kind()) {
            case CLASS:  return new Color(0x4ec9b0);
            case METHOD: return new Color(0xdcdcaa);
            case FIELD:  return new Color(0x9cdcfe);
            default:     return Color.LIGHT_GRAY;
        }
    }

    private NavigableToken tokenAt(Point point) {
        int offset;
        try {
            offset = viewToModel(point);
        } catch (Exception e) {
            return null;
        }
        for (NavigableToken t : tokens) {
            if (t.contains(offset)) return t;
        }
        return null;
    }

    private void navigateTo(NavigableToken token) {
        if (token == null) return;
        JarArchive archive = jbm.getJarArchive();
        if (archive == null || archive.getClasses() == null) return;

        ClassNode cn = archive.getClasses().get(token.owner());
        if (cn == null) return;

        switch (token.kind()) {
            case CLASS: {
                jbm.selectMethod(cn, null);
                break;
            }
            case METHOD: {
                MethodNode mn = findMethod(cn, token.name(), token.descriptor());
                if (mn != null) jbm.selectMethod(cn, mn);
                else jbm.selectMethod(cn, null);
                break;
            }
            case FIELD: {
                jbm.selectMethod(cn, null);
                break;
            }
        }
    }

    private MethodNode findMethod(ClassNode cn, String name, String desc) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals(name) && (desc == null || mn.desc.equals(desc))) return mn;
        }
        return null;
    }

    private void updateHover(Point point) {
        NavigableToken prev = hoveredToken;
        hoveredToken = tokenAt(point);
        if (hoveredToken != prev) {
            if (hoveredToken != null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(buildTooltip(hoveredToken));
            } else {
                setCursor(Cursor.getDefaultCursor());
                setToolTipText(null);
            }
            repaint();
        }
    }

    private String buildTooltip(NavigableToken token) {
        switch (token.kind()) {
            case CLASS:  return "<html><b>Class</b> " + token.owner().replace('/', '.') + "</html>";
            case METHOD: return "<html><b>Method</b> " + token.owner().replace('/', '.') + "#" + token.name()
                    + "<br><font color='gray'>" + token.descriptor() + "</font></html>";
            case FIELD:  return "<html><b>Field</b> " + token.owner().replace('/', '.') + "#" + token.name()
                    + "<br><font color='gray'>" + token.descriptor() + "</font></html>";
            default:     return token.displayName();
        }
    }

    private final class Handler extends MouseAdapter {

        @Override
        public void mouseMoved(MouseEvent e) {
            updateHover(e.getPoint());
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (hoveredToken != null) {
                hoveredToken = null;
                setCursor(Cursor.getDefaultCursor());
                setToolTipText(null);
                repaint();
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            boolean isCtrl  = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
            boolean isLeft  = e.getButton() == MouseEvent.BUTTON1;
            boolean isDblClk = e.getClickCount() == 2;

            if (isLeft && (isCtrl || isDblClk)) {
                NavigableToken token = tokenAt(e.getPoint());
                if (token != null) {
                    e.consume();
                    navigateTo(token);
                }
            }
        }
    }
}
