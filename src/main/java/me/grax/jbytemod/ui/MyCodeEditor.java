package me.grax.jbytemod.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.ui.lists.AdressList;
import me.grax.jbytemod.bookmark.BookmarkGutter;
import me.grax.jbytemod.ui.lists.ErrorList;
import me.grax.jbytemod.ui.lists.MyCodeList;

import javax.swing.*;
import java.awt.*;

public class MyCodeEditor extends JPanel {
    private MyCodeList cl;

    public MyCodeEditor(JByteMod jbm, JLabel editor) {
        this.setLayout(new BorderLayout());
        cl = new MyCodeList(jbm, editor);
        this.add(cl, BorderLayout.CENTER);

        JPanel left = new JPanel();
        left.setLayout(new BorderLayout());
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("nimbusBorder")));

        BookmarkGutter gutter = new BookmarkGutter(cl);
        cl.setBookmarkGutter(gutter);

        left.add(new AdressList(cl), BorderLayout.CENTER);
        left.add(gutter, BorderLayout.WEST);

        this.add(left, BorderLayout.WEST);
        this.add(new ErrorList(jbm, cl), BorderLayout.EAST);
    }

    public MyCodeList getEditor() {
        return cl;
    }
}
