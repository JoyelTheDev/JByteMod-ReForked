package dev.joyel.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public final class Minimap extends JComponent {

    private static final int PANEL_WIDTH = 120;
    private static final int PADDING = 6;
    private static final int MAX_ROWS = 12000;
    private static final int TAB_COLUMNS = 4;
    private static final int TOOLTIP_LIMIT = 140;

    private final RSyntaxTextArea area;
    private final JScrollPane scroll;
    private final Timer rebuildTimer;
    private final DocumentListener documentListener = new DocumentListener() {
        @Override
        public void insertUpdate(DocumentEvent e) {
            scheduleRebuild();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            scheduleRebuild();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            scheduleRebuild();
        }
    };

    private Document boundDocument;
    private BufferedImage image;
    private int rows;
    private int step = 1;
    private int lineHeight = 3;
    private boolean dirty = true;
    private Integer frozenOffset;

    public Minimap(RSyntaxTextArea area, JScrollPane scroll) {
        this.area = area;
        this.scroll = scroll;
        this.rebuildTimer = new Timer(140, e -> {
            if (isShowing()) {
                rebuild();
            }
        });
        this.rebuildTimer.setRepeats(false);

        setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        setMinimumSize(new Dimension(PANEL_WIDTH, 0));
        ToolTipManager.sharedInstance().registerComponent(this);

        bindDocument();
        area.addPropertyChangeListener(evt -> {
            String name = evt.getPropertyName();
            if ("document".equals(name)) {
                bindDocument();
                scheduleRebuild();
            } else if (RSyntaxTextArea.SYNTAX_SCHEME_PROPERTY.equals(name) || "background".equals(name) || "foreground".equals(name)) {
                scheduleRebuild();
            }
        });
        area.addCaretListener(e -> repaint());
        scroll.getViewport().addChangeListener(e -> repaint());
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> repaint());
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && dirty) {
                scheduleRebuild();
            }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || rows == 0) {
                    return;
                }
                frozenOffset = currentOffset();
                scrollToLine(lineForY(e.getY(), frozenOffset));
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (frozenOffset == null || rows == 0) {
                    return;
                }
                scrollToLine(lineForY(e.getY(), frozenOffset));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                frozenOffset = null;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        scheduleRebuild();
    }

    private void bindDocument() {
        if (boundDocument != null) {
            boundDocument.removeDocumentListener(documentListener);
        }
        boundDocument = area.getDocument();
        if (boundDocument != null) {
            boundDocument.addDocumentListener(documentListener);
        }
    }

    private void scheduleRebuild() {
        dirty = true;
        rebuildTimer.restart();
    }

    private void rebuild() {
        dirty = false;
        int lines = area.getLineCount();
        if (lines <= 0 || area.getDocument().getLength() == 0) {
            image = null;
            rows = 0;
            repaint();
            return;
        }
        step = Math.max(1, (lines + MAX_ROWS - 1) / MAX_ROWS);
        rows = (lines + step - 1) / step;
        lineHeight = rows <= 3000 ? 3 : rows <= 6000 ? 2 : 1;
        int barHeight = Math.max(1, lineHeight - 1);
        int imageWidth = PANEL_WIDTH - PADDING - 4;

        BufferedImage buffer = new BufferedImage(imageWidth, rows * lineHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = buffer.createGraphics();
        Color fallback = area.getForeground();
        for (int row = 0; row < rows; row++) {
            Token token = area.getTokenListForLine(row * step);
            int column = 0;
            int y = row * lineHeight;
            while (token != null && token.isPaintable() && column < imageWidth) {
                int width = columns(token);
                if (token.getType() != TokenTypes.WHITESPACE && width > 0) {
                    Color color = area.getForegroundForToken(token);
                    g.setColor(EditorExtras.withAlpha(color != null ? color : fallback, 190));
                    g.fillRect(column, y, Math.min(width, imageWidth - column), barHeight);
                }
                column += width;
                token = token.getNextToken();
            }
        }
        g.dispose();
        image = buffer;
        repaint();
    }

    private static int columns(Token token) {
        char[] text = token.getTextArray();
        int offset = token.getTextOffset();
        int length = token.length();
        int columns = 0;
        for (int i = 0; i < length; i++) {
            columns += text[offset + i] == '\t' ? TAB_COLUMNS : 1;
        }
        return columns;
    }

    private int lineAt(int y) {
        try {
            int offset = area.viewToModel2D(new Point(0, Math.max(0, y)));
            return area.getLineOfOffset(offset);
        } catch (BadLocationException e) {
            return 0;
        }
    }

    private int topLine() {
        return lineAt(area.getVisibleRect().y);
    }

    private int bottomLine() {
        Rectangle visible = area.getVisibleRect();
        return lineAt(visible.y + visible.height);
    }

    private int currentOffset() {
        if (frozenOffset != null) {
            return frozenOffset;
        }
        int content = rows * lineHeight;
        int height = getHeight();
        if (content <= height) {
            return 0;
        }
        int lines = area.getLineCount();
        int visibleLines = Math.max(1, bottomLine() - topLine() + 1);
        int maxTop = Math.max(1, lines - visibleLines);
        double fraction = Math.min(1.0, Math.max(0.0, topLine() / (double) maxTop));
        return (int) Math.round((content - height) * fraction);
    }

    private int lineForY(int y, int offset) {
        int row = Math.max(0, Math.min(rows - 1, (y + offset) / lineHeight));
        return Math.min(Math.max(0, area.getLineCount() - 1), row * step);
    }

    private void scrollToLine(int line) {
        try {
            Rectangle2D rect = area.modelToView2D(area.getLineStartOffset(line));
            if (rect == null) {
                return;
            }
            JViewport viewport = scroll.getViewport();
            int target = (int) rect.getY() + (int) (rect.getHeight() / 2) - viewport.getHeight() / 2;
            int max = Math.max(0, area.getHeight() - viewport.getHeight());
            Point current = viewport.getViewPosition();
            viewport.setViewPosition(new Point(current.x, Math.max(0, Math.min(max, target))));
        } catch (BadLocationException ignored) {
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (rows == 0) {
            return null;
        }
        int line = lineForY(event.getY(), currentOffset());
        try {
            int start = area.getLineStartOffset(line);
            int end = area.getLineEndOffset(line);
            String text = area.getText(start, Math.max(0, end - start)).strip();
            if (text.length() > TOOLTIP_LIMIT) {
                text = text.substring(0, TOOLTIP_LIMIT) + "\u2026";
            }
            return text.isEmpty() ? "Line " + (line + 1) : "Line " + (line + 1) + ": " + text;
        } catch (BadLocationException e) {
            return null;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int width = getWidth();
        int height = getHeight();
        Color background = area.getBackground();
        Color foreground = area.getForeground();
        g2.setColor(EditorExtras.mix(background, foreground, 0.05f));
        g2.fillRect(0, 0, width, height);

        if (dirty && !rebuildTimer.isRunning()) {
            rebuildTimer.restart();
        }

        if (image != null) {
            int offset = currentOffset();
            int visibleRows = Math.max(0, Math.min(height, image.getHeight() - offset));
            if (visibleRows > 0) {
                g2.drawImage(image, PADDING, 0, PADDING + image.getWidth(), visibleRows,
                        0, offset, image.getWidth(), offset + visibleRows, null);
            }
            paintCaret(g2, offset, width);
            paintViewport(g2, offset, width, foreground);
        }

        g2.setColor(EditorExtras.withAlpha(foreground, 40));
        g2.drawLine(0, 0, 0, height);
        g2.dispose();
    }

    private void paintViewport(Graphics2D g2, int offset, int width, Color foreground) {
        int top = topLine();
        int bottom = bottomLine();
        int y1 = (top / step) * lineHeight - offset;
        int y2 = ((bottom / step) + 1) * lineHeight - offset;
        int boxHeight = Math.max(12, y2 - y1);
        g2.setColor(EditorExtras.withAlpha(foreground, frozenOffset != null ? 52 : 34));
        g2.fillRect(0, y1, width, boxHeight);
        g2.setColor(EditorExtras.withAlpha(EditorExtras.accent(), 200));
        g2.fillRect(0, y1, 2, boxHeight);
    }

    private void paintCaret(Graphics2D g2, int offset, int width) {
        int line = area.getCaretLineNumber();
        int y = (line / step) * lineHeight - offset;
        Color accent = EditorExtras.accent();
        g2.setColor(EditorExtras.withAlpha(accent, 70));
        g2.fillRect(0, y, width, Math.max(2, lineHeight));
        g2.setColor(EditorExtras.withAlpha(accent, 230));
        g2.fillRect(width - 3, y, 3, Math.max(2, lineHeight));
    }
}
