package me.grax.jbytemod.bookmark;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.utils.list.LazyListModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class BookmarkPanel extends JList<Bookmark> {

    private final JByteMod jbm;

    public BookmarkPanel(JByteMod jbm) {
        super(new LazyListModel<>());
        this.jbm = jbm;
        this.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        BookmarkManager.get().addListener(this::refresh);
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Bookmark b = getSelectedValue();
                if (b == null) return;
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    navigate(b);
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    showMenu(b, e);
                }
            }
        });
        refresh();
    }

    private void navigate(Bookmark b) {
        jbm.selectMethod(b.getClassNode(), b.getMethodNode());
        jbm.treeSelection(b.getClassNode(), b.getMethodNode());
        SwingUtilities.invokeLater(() -> {
            ListModel<me.grax.jbytemod.ui.lists.entries.InstrEntry> model =
                    jbm.getCodeList().getModel();
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).getInstr() == b.getInsnNode()) {
                    jbm.getCodeList().setSelectedIndex(i);
                    jbm.getCodeList().ensureIndexIsVisible(i);
                    break;
                }
            }
        });
    }

    private void showMenu(Bookmark b, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem goTo = new JMenuItem("Go to");
        goTo.addActionListener(ev -> navigate(b));
        menu.add(goTo);

        JMenuItem rename = new JMenuItem("Rename");
        rename.addActionListener(ev -> {
            String newLabel = JOptionPane.showInputDialog(jbm, "Label:", b.getLabel());
            if (newLabel != null) {
                b.setLabel(newLabel);
                BookmarkManager.get().addListener(() -> {});
                refresh();
            }
        });
        menu.add(rename);

        JMenuItem remove = new JMenuItem("Remove");
        remove.addActionListener(ev -> BookmarkManager.get().remove(b.getInsnNode()));
        menu.add(remove);

        menu.show(this, e.getX(), e.getY());
    }

    public void refresh() {
        LazyListModel<Bookmark> model = new LazyListModel<>();
        for (Bookmark b : BookmarkManager.get().getAll()) {
            model.addElement(b);
        }
        setModel(model);
    }
}
