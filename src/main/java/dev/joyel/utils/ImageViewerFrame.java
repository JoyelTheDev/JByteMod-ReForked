package dev.joyel.utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public final class ImageViewerFrame extends JFrame {

    private static final float MIN_ZOOM    = 0.01f;
    private static final float MAX_ZOOM    = 32f;
    private static final float ZOOM_STEP   = 1.15f;
    private static final int   CHECKER_SIZE = 12;

    private static final Color CHECKER_A = new Color(0x3a3a3a);
    private static final Color CHECKER_B = new Color(0x2a2a2a);
    private static final Color BG_COLOR  = new Color(0x1e1e1e);

    private final String entryName;
    private BufferedImage image;
    private String loadError;

    private float zoom = 1f;
    private float panX = 0f;
    private float panY = 0f;
    private boolean fitDone = false;

    private final ImageCanvas canvas;
    private final JLabel infoLabel;
    private final JLabel zoomLabel;

    public ImageViewerFrame(String entryName, byte[] data) {
        super("Image Viewer \u2013 " + shortName(entryName));
        this.entryName = entryName;

        loadImage(data);

        canvas    = new ImageCanvas();
        infoLabel = new JLabel(" ");
        zoomLabel = new JLabel("100%");

        infoLabel.setForeground(Color.GRAY);
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 11f));
        zoomLabel.setForeground(Color.GRAY);
        zoomLabel.setFont(zoomLabel.getFont().deriveFont(Font.PLAIN, 11f));

        if (image != null) {
            infoLabel.setText(" " + image.getWidth() + " \u00d7 " + image.getHeight()
                    + "  |  " + shortName(entryName));
        } else {
            infoLabel.setText(" " + (loadError != null ? loadError : "Unknown error"));
        }

        JButton fitBtn   = toolButton("Fit",    new Runnable() { public void run() { fitToWindow(); } });
        JButton zoomInBtn  = toolButton("+",    new Runnable() { public void run() { setZoom(zoom * ZOOM_STEP); } });
        JButton zoomOutBtn = toolButton("-",    new Runnable() { public void run() { setZoom(zoom / ZOOM_STEP); } });
        JButton resetBtn = toolButton("1:1",    new Runnable() { public void run() { resetZoom(); } });
        JButton centerBtn = toolButton("Center", new Runnable() { public void run() { centerImage(); } });

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(new EmptyBorder(3, 5, 3, 5));
        toolbar.add(fitBtn);
        toolbar.add(zoomOutBtn);
        toolbar.add(zoomLabel);
        toolbar.add(zoomInBtn);
        toolbar.add(resetBtn);
        toolbar.addSeparator(new Dimension(8, 0));
        toolbar.add(centerBtn);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(2, 0, 2, 6));
        statusBar.add(infoLabel, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBackground(BG_COLOR);
        content.add(toolbar,   BorderLayout.NORTH);
        content.add(canvas,    BorderLayout.CENTER);
        content.add(statusBar, BorderLayout.SOUTH);

        setContentPane(content);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(820, 620);
        setLocationRelativeTo(null);

        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) { dispose(); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) { fitToWindow(); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_F, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) { setZoom(zoom * ZOOM_STEP); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) { setZoom(zoom / ZOOM_STEP); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(
                new ActionListener() { public void actionPerformed(ActionEvent e) { resetZoom(); } },
                KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public static void open(String entryName, byte[] data) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new ImageViewerFrame(entryName, data).setVisible(true);
            }
        });
    }

    public static boolean isImageEntry(String entryName) {
        if (entryName == null) return false;
        String lower = entryName.toLowerCase();
        return lower.endsWith(".png")  || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".bmp")  || lower.endsWith(".webp")
                || lower.endsWith(".ico")  || lower.endsWith(".tiff")
                || lower.endsWith(".tif");
    }

    private void loadImage(byte[] data) {
        if (data == null || data.length == 0) {
            loadError = "No data for this entry.";
            return;
        }
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            image = ImageIO.read(bis);
            if (image == null) {
                loadError = "Format not supported by ImageIO.";
            }
        } catch (IOException e) {
            loadError = "Failed to decode image: " + e.getMessage();
        }
    }

    private void fitToWindow() {
        if (image == null) return;
        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        if (cw <= 0 || ch <= 0) return;
        float sx = (float) cw / image.getWidth();
        float sy = (float) ch / image.getHeight();
        zoom = Math.max(MIN_ZOOM, Math.min(1f, Math.min(sx, sy)));
        centerImage();
        updateZoomLabel();
    }

    private void resetZoom() {
        zoom = 1f;
        centerImage();
        updateZoomLabel();
    }

    private void centerImage() {
        panX = 0f;
        panY = 0f;
        canvas.repaint();
    }

    private void setZoom(float newZoom) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        updateZoomLabel();
        canvas.repaint();
    }

    private void updateZoomLabel() {
        zoomLabel.setText(Math.round(zoom * 100f) + "%");
    }

    private static String shortName(String path) {
        int i = path.lastIndexOf('/');
        return i == -1 ? path : path.substring(i + 1);
    }

    private static JButton toolButton(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
        return b;
    }

    private final class ImageCanvas extends JPanel {

        private Point lastMouse;
        private boolean dragging;

        ImageCanvas() {
            setBackground(BG_COLOR);
            setFocusable(true);

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (e.getButton() == MouseEvent.BUTTON1
                            || e.getButton() == MouseEvent.BUTTON2) {
                        lastMouse = e.getPoint();
                        dragging  = true;
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                    if (SwingUtilities.isRightMouseButton(e)) {
                        showContextMenu(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging  = false;
                    lastMouse = null;
                    setCursor(Cursor.getDefaultCursor());
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging || lastMouse == null) return;
                    panX += e.getX() - lastMouse.x;
                    panY += e.getY() - lastMouse.y;
                    lastMouse = e.getPoint();
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (image == null) return;
                    float oldZoom = zoom;
                    float factor  = e.getWheelRotation() < 0 ? ZOOM_STEP : 1f / ZOOM_STEP;
                    float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
                    float mx = e.getX();
                    float my = e.getY();
                    float cx = getWidth()  * 0.5f + panX;
                    float cy = getHeight() * 0.5f + panY;
                    float imgOffX = mx - cx;
                    float imgOffY = my - cy;
                    panX += imgOffX - imgOffX * (newZoom / oldZoom);
                    panY += imgOffY - imgOffY * (newZoom / oldZoom);
                    zoom = newZoom;
                    updateZoomLabel();
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);

            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (!fitDone && image != null) {
                        fitDone = true;
                        fitToWindow();
                    }
                }
            });
        }

        private void showContextMenu(int x, int y) {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem fitItem   = new JMenuItem("Fit to Window  [F]");
            JMenuItem resetItem = new JMenuItem("Actual Size (1:1)  [Ctrl+0]");
            JMenuItem zoomIn    = new JMenuItem("Zoom In  [Ctrl++]");
            JMenuItem zoomOut   = new JMenuItem("Zoom Out  [Ctrl+-]");
            JMenuItem centerItem = new JMenuItem("Center");

            fitItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { fitToWindow(); }
            });
            resetItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { resetZoom(); }
            });
            zoomIn.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { setZoom(zoom * ZOOM_STEP); }
            });
            zoomOut.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { setZoom(zoom / ZOOM_STEP); }
            });
            centerItem.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) { centerImage(); }
            });

            menu.add(fitItem);
            menu.add(resetItem);
            menu.addSeparator();
            menu.add(zoomIn);
            menu.add(zoomOut);
            menu.addSeparator();
            menu.add(centerItem);
            menu.show(this, x, y);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,       RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY);

            if (image == null) {
                drawError(g2);
                g2.dispose();
                return;
            }

            int displayW = Math.max(1, Math.round(image.getWidth()  * zoom));
            int displayH = Math.max(1, Math.round(image.getHeight() * zoom));
            int imgX     = Math.round(getWidth()  * 0.5f - displayW * 0.5f + panX);
            int imgY     = Math.round(getHeight() * 0.5f - displayH * 0.5f + panY);

            drawCheckerboard(g2, imgX, imgY, displayW, displayH);

            AffineTransform at = AffineTransform.getTranslateInstance(imgX, imgY);
            at.scale(zoom, zoom);
            g2.drawRenderedImage(image, at);

            drawBorder(g2, imgX, imgY, displayW, displayH);
            drawZoomBadge(g2);

            g2.dispose();
        }

        private void drawCheckerboard(Graphics2D g2, int x, int y, int w, int h) {
            int cols = (int) Math.ceil((double) w / CHECKER_SIZE);
            int rows = (int) Math.ceil((double) h / CHECKER_SIZE);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int tx = x + col * CHECKER_SIZE;
                    int ty = y + row * CHECKER_SIZE;
                    int tw = Math.min(CHECKER_SIZE, x + w - tx);
                    int th = Math.min(CHECKER_SIZE, y + h - ty);
                    if (tw <= 0 || th <= 0) continue;
                    g2.setColor(((row + col) % 2 == 0) ? CHECKER_A : CHECKER_B);
                    g2.fillRect(tx, ty, tw, th);
                }
            }
        }

        private void drawBorder(Graphics2D g2, int x, int y, int w, int h) {
            g2.setColor(new Color(0x555555));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(x, y, w, h);
        }

        private void drawZoomBadge(Graphics2D g2) {
            String label = Math.round(zoom * 100f) + "%";
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, 11);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(label);
            int th = fm.getAscent();
            int padX = 7, padY = 4, margin = 8;
            int bx = getWidth()  - tw - padX * 2 - margin;
            int by = margin;
            int bw = tw + padX * 2;
            int bh = th + padY * 2;

            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(bx, by, bw, bh, 6, 6);
            g2.setColor(new Color(0xcccccc));
            g2.drawString(label, bx + padX, by + padY + th);
        }

        private void drawError(Graphics2D g2) {
            String msg = loadError != null ? loadError : "Failed to load image.";
            g2.setColor(new Color(0xe05555));
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth()  - fm.stringWidth(msg)) / 2;
            int y = (getHeight() + fm.getAscent())       / 2;
            g2.drawString(msg, x, y);
        }
    }
}
