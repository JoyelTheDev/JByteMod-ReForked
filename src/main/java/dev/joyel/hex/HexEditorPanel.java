package dev.joyel.hex;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public final class HexEditorPanel extends JComponent implements Scrollable {

    private static final int BYTES_PER_ROW = 16;
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private byte[] data;
    private boolean readOnly;

    private int cursorByte = 0;
    private boolean cursorHighNibble = true;
    private int selStart = -1;
    private int selEnd   = -1;
    private boolean inHexRegion = true;

    private int charW;
    private int lineH;
    private int addrX;
    private int hexStartX;
    private int asciiStartX;
    private int totalWidth;
    private int ascent;

    private final List<ChangeListener> changeListeners = new ArrayList<ChangeListener>();

    public HexEditorPanel(byte[] data, boolean readOnly) {
        this.data = data != null ? data.clone() : new byte[0];
        this.readOnly = readOnly;
        setFont(MONO);
        setFocusable(true);
        setBackground(new Color(0x1e, 0x1e, 0x1e));
        setForeground(new Color(0xd4, 0xd4, 0xd4));
        setOpaque(true);
        computeMetrics();

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                int idx = byteAt(e.getX(), e.getY());
                if (idx < 0) return;
                cursorByte = idx;
                cursorHighNibble = true;
                if (e.isShiftDown()) {
                    if (selStart < 0) selStart = idx;
                    selEnd = idx;
                } else {
                    selStart = idx;
                    selEnd   = idx;
                }
                inHexRegion = e.getX() < asciiStartX;
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                int idx = byteAt(e.getX(), e.getY());
                if (idx >= 0) {
                    selEnd = idx;
                    cursorByte = idx;
                    repaint();
                }
            }
        });

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e)  { handleKey(e); }
            public void keyTyped(KeyEvent e)    { handleType(e.getKeyChar()); }
        });
    }

    public void addChangeListener(ChangeListener l) { changeListeners.add(l); }

    private void fireChange() {
        ChangeEvent ev = new ChangeEvent(this);
        for (ChangeListener l : changeListeners) l.stateChanged(ev);
    }

    public byte[] getData() { return data.clone(); }

    public void setData(byte[] newData) {
        this.data = newData != null ? newData.clone() : new byte[0];
        cursorByte = Math.min(cursorByte, Math.max(0, data.length - 1));
        selStart = -1;
        selEnd   = -1;
        revalidate();
        repaint();
    }

    public void goTo(int offset) {
        offset = Math.max(0, Math.min(data.length - 1, offset));
        cursorByte = offset;
        cursorHighNibble = true;
        selStart = offset;
        selEnd   = offset;
        ensureByteVisible(offset);
        repaint();
    }

    public int getCursorOffset() { return cursorByte; }
    public boolean isReadOnly()  { return readOnly; }
    public void setReadOnly(boolean ro) { readOnly = ro; repaint(); }

    private void computeMetrics() {
        FontMetrics fm = getFontMetrics(MONO);
        charW      = fm.charWidth('0');
        lineH      = fm.getHeight() + 2;
        ascent     = fm.getAscent();
        addrX      = 4;
        hexStartX  = addrX + 10 * charW;
        asciiStartX = hexStartX + BYTES_PER_ROW * 3 * charW + charW;
        totalWidth = asciiStartX + BYTES_PER_ROW * charW + 8;
    }

    private int rowCount() {
        return data.length == 0 ? 1 : (data.length + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
    }

    @Override
    public Dimension getPreferredSize() {
        computeMetrics();
        return new Dimension(totalWidth, rowCount() * lineH + lineH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        computeMetrics();
        Graphics2D g2 = (Graphics2D) g;
        g2.setFont(MONO);

        Rectangle clip = g2.getClipBounds();
        g2.setColor(getBackground());
        g2.fillRect(clip.x, clip.y, clip.width, clip.height);

        int selLow  = selStart >= 0 ? Math.min(selStart, selEnd) : -1;
        int selHigh = selStart >= 0 ? Math.max(selStart, selEnd) : -1;

        int firstRow = clip.y / lineH;
        int lastRow  = Math.min(rowCount(), (clip.y + clip.height) / lineH + 1);

        // divider lines
        g2.setColor(new Color(0x44, 0x44, 0x44));
        g2.drawLine(addrX + 9 * charW, clip.y, addrX + 9 * charW, clip.y + clip.height);
        g2.drawLine(asciiStartX - charW, clip.y, asciiStartX - charW, clip.y + clip.height);

        for (int row = firstRow; row < lastRow; row++) {
            int y       = row * lineH + ascent;
            int rowBase = row * BYTES_PER_ROW;

            // address
            g2.setColor(new Color(0x56, 0x9c, 0xd6));
            g2.drawString(String.format("%08X", rowBase), addrX, y);

            for (int col = 0; col < BYTES_PER_ROW; col++) {
                int idx = rowBase + col;
                if (idx >= data.length) break;

                int hexX   = hexStartX + col * 3 * charW;
                int asciiX = asciiStartX + col * charW;

                boolean sel    = selLow >= 0 && idx >= selLow && idx <= selHigh;
                boolean cursor = idx == cursorByte;

                if (sel) {
                    g2.setColor(new Color(0x21, 0x42, 0x83));
                    g2.fillRect(hexX,   row * lineH, charW * 2, lineH);
                    g2.fillRect(asciiX, row * lineH, charW, lineH);
                }
                if (cursor && hasFocus()) {
                    g2.setColor(new Color(0xff, 0xd7, 0x00, 70));
                    g2.fillRect(hexX,   row * lineH, charW * 2, lineH);
                    g2.fillRect(asciiX, row * lineH, charW, lineH);
                    if (!readOnly && inHexRegion) {
                        int cx = hexX + (cursorHighNibble ? 0 : charW);
                        g2.setColor(Color.WHITE);
                        g2.fillRect(cx, row * lineH + lineH - 2, charW, 2);
                    }
                }

                int unsigned = data[idx] & 0xFF;
                g2.setColor(byteColor(unsigned));
                g2.drawString(String.format("%02X", unsigned), hexX, y);

                char ascii = (unsigned >= 32 && unsigned < 127) ? (char) unsigned : '.';
                g2.setColor(ascii == '.' ? new Color(0x55, 0x55, 0x55) : new Color(0xce, 0x91, 0x78));
                g2.drawString(String.valueOf(ascii), asciiX, y);
            }
        }
    }

    private Color byteColor(int u) {
        if (u == 0)           return new Color(0x55, 0x55, 0x55);
        if (u < 32 || u == 127) return new Color(0x9c, 0xdc, 0xfe);
        if (u >= 128)         return new Color(0x4e, 0xc9, 0xb0);
        return new Color(0xd4, 0xd4, 0xd4);
    }

    private int byteAt(int mx, int my) {
        int row = my / lineH;
        if (row < 0 || row >= rowCount()) return -1;
        int col = -1;
        if (mx >= hexStartX && mx < asciiStartX - charW) {
            col = (mx - hexStartX) / (charW * 3);
        } else if (mx >= asciiStartX) {
            col = (mx - asciiStartX) / charW;
        }
        if (col < 0 || col >= BYTES_PER_ROW) return -1;
        int idx = row * BYTES_PER_ROW + col;
        return idx < data.length ? idx : -1;
    }

    private void handleKey(KeyEvent e) {
        int key   = e.getKeyCode();
        boolean sh  = e.isShiftDown();
        boolean ctrl = e.isControlDown();

        if (ctrl && key == KeyEvent.VK_C) { copySelection(); return; }
        if (ctrl && key == KeyEvent.VK_A) { selStart = 0; selEnd = data.length - 1; repaint(); return; }

        if      (key == KeyEvent.VK_LEFT)      moveCursor(-1, sh);
        else if (key == KeyEvent.VK_RIGHT)     moveCursor(1, sh);
        else if (key == KeyEvent.VK_UP)        moveCursor(-BYTES_PER_ROW, sh);
        else if (key == KeyEvent.VK_DOWN)      moveCursor(BYTES_PER_ROW, sh);
        else if (key == KeyEvent.VK_PAGE_UP)   moveCursor(-BYTES_PER_ROW * 16, sh);
        else if (key == KeyEvent.VK_PAGE_DOWN) moveCursor(BYTES_PER_ROW * 16, sh);
        else if (key == KeyEvent.VK_HOME)
            moveCursorTo(ctrl ? 0 : (cursorByte / BYTES_PER_ROW) * BYTES_PER_ROW, sh);
        else if (key == KeyEvent.VK_END) {
            int end = ctrl ? data.length - 1
                           : Math.min(data.length - 1, (cursorByte / BYTES_PER_ROW + 1) * BYTES_PER_ROW - 1);
            moveCursorTo(end, sh);
        } else if (!readOnly && key == KeyEvent.VK_DELETE)    deleteForward();
        else if (!readOnly && key == KeyEvent.VK_BACK_SPACE)  deleteBack();
    }

    private void handleType(char c) {
        if (readOnly || data.length == 0) return;
        if (inHexRegion) {
            char up = Character.toUpperCase(c);
            if (!isHexChar(up)) return;
            int nibble = Character.digit(up, 16);
            byte cur = data[cursorByte];
            if (cursorHighNibble) {
                data[cursorByte] = (byte) ((nibble << 4) | (cur & 0x0F));
                cursorHighNibble = false;
            } else {
                data[cursorByte] = (byte) ((cur & 0xF0) | nibble);
                cursorHighNibble = true;
                if (cursorByte < data.length - 1) cursorByte++;
            }
        } else {
            if (c >= 32 && c < 127) {
                data[cursorByte] = (byte) c;
                if (cursorByte < data.length - 1) cursorByte++;
            }
        }
        fireChange();
        repaint();
    }

    private boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
    }

    private void moveCursor(int delta, boolean extend) {
        moveCursorTo(cursorByte + delta, extend);
    }

    private void moveCursorTo(int target, boolean extend) {
        target = Math.max(0, Math.min(data.length - 1, target));
        if (extend) {
            if (selStart < 0) selStart = cursorByte;
            selEnd = target;
        } else {
            selStart = target;
            selEnd   = target;
        }
        cursorByte = target;
        cursorHighNibble = true;
        ensureByteVisible(cursorByte);
        repaint();
    }

    private void ensureByteVisible(int byteIdx) {
        int row = byteIdx / BYTES_PER_ROW;
        scrollRectToVisible(new Rectangle(0, row * lineH, totalWidth, lineH * 2));
    }

    private void deleteForward() {
        if (cursorByte >= data.length) return;
        byte[] n = new byte[data.length - 1];
        System.arraycopy(data, 0, n, 0, cursorByte);
        System.arraycopy(data, cursorByte + 1, n, cursorByte, data.length - cursorByte - 1);
        data = n;
        if (cursorByte >= data.length && cursorByte > 0) cursorByte--;
        fireChange();
        revalidate();
        repaint();
    }

    private void deleteBack() {
        if (cursorByte <= 0 || data.length == 0) return;
        cursorByte--;
        deleteForward();
    }

    private void copySelection() {
        int low  = selStart >= 0 ? Math.min(selStart, selEnd) : cursorByte;
        int high = selStart >= 0 ? Math.max(selStart, selEnd) : cursorByte;
        StringBuilder sb = new StringBuilder();
        for (int i = low; i <= high && i < data.length; i++) {
            if (i > low) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(sb.toString()), null);
    }

    @Override 
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(totalWidth, lineH * 24);
    }
    @Override 
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
        return o == SwingConstants.VERTICAL ? lineH : charW;
    }
    @Override 
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
        return o == SwingConstants.VERTICAL ? lineH * 16 : charW * 8;
    }
    @Override 
    public boolean getScrollableTracksViewportWidth()  { return false; }
    @Override 
    public boolean getScrollableTracksViewportHeight() { return false; }
}
