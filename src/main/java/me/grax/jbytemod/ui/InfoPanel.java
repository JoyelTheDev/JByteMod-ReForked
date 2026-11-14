package me.grax.jbytemod.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.ui.ifs.*;
import me.grax.jbytemod.utils.gui.LookUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.awt.*;

public class InfoPanel extends JPanel {

    private JDesktopPane deskPane;
    private JByteMod jbm;

    public InfoPanel(JByteMod jbm) {
        this.jbm = jbm;
        this.setLayout(new BorderLayout());
        deskPane = new JDesktopPane() {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(BorderFactory.createLineBorder(LookUtils.borderColor()));
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Color background = UIManager.getColor("Panel.background");
                g.setColor(background != null ? background : getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }

        };
        deskPane.setDesktopManager(new DeskMan());
        this.add(deskPane, BorderLayout.CENTER);
    }

    public void selectMethod(ClassNode cn, MethodNode mn) {
        for (Component c : deskPane.getComponents()) {
            if (c instanceof MyInternalFrame) {
                c.setVisible(false);
            }
        }
        deskPane.removeAll();
        deskPane.add(new TCBFrame(jbm.getTcbList()));
        deskPane.add(new LVPFrame(jbm.getLvpList()));
        deskPane.add(new MNSettings(cn, mn));

        this.repaint();
    }

    public void selectClass(ClassNode cn) {
        for (Component c : deskPane.getComponents()) {
            if (c instanceof MyInternalFrame) {
                c.setVisible(false);
            }
        }
        deskPane.removeAll();
        deskPane.add(new CNSettings(cn));
        this.repaint();
    }

    class DeskMan extends DefaultDesktopManager {

        @Override
        public void beginDraggingFrame(JComponent f) {
        }

        @Override
        public void beginResizingFrame(JComponent f, int direction) {
        }

        @Override
        public void setBoundsForFrame(JComponent f, int newX, int newY, int newWidth, int newHeight) {
            if (!(f instanceof JInternalFrame)) {
                return;
            }
            boolean didResize = (f.getWidth() != newWidth || f.getHeight() != newHeight);
            if (!inBounds((JInternalFrame) f, newX, newY, newWidth, newHeight)) {
                Container parent = f.getParent();
                Dimension parentSize = parent.getSize();
                int boundedX = (int) Math.min(Math.max(0, newX), parentSize.getWidth() - newWidth);
                int boundedY = (int) Math.min(Math.max(0, newY), parentSize.getHeight() - newHeight);
                f.setBounds(boundedX, boundedY, newWidth, newHeight);
            } else {
                f.setBounds(newX, newY, newWidth, newHeight);
            }
            if (didResize) {
                f.validate();
            }
        }

        protected boolean inBounds(JInternalFrame f, int newX, int newY, int newWidth, int newHeight) {
            if (newX < 0 || newY < 0)
                return false;
            if (newX + newWidth > f.getDesktopPane().getWidth())
                return false;
            if (newY + newHeight > f.getDesktopPane().getHeight())
                return false;
            return true;
        }
    }
}
