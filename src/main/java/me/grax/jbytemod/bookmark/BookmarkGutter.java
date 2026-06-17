package me.grax.jbytemod.bookmark;

import me.grax.jbytemod.ui.lists.MyCodeList;
import me.grax.jbytemod.ui.lists.entries.InstrEntry;
import me.grax.jbytemod.utils.gui.SwingUtils;
import me.grax.jbytemod.utils.list.LazyListModel;

import javax.swing.*;
import java.awt.*;

public class BookmarkGutter extends JList<String> {

    private static final String MARKED = "\u2605";
    private static final String EMPTY = " ";

    private final MyCodeList codeList;

    public BookmarkGutter(MyCodeList codeList) {
        super(new DefaultListModel<>());
        this.codeList = codeList;
        this.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        this.setForeground(new Color(255, 200, 0));
        this.setPrototypeCellValue(MARKED);
        this.setSelectionModel(new DefaultListSelectionModel() {
            @Override
            public void setSelectionInterval(int index0, int index1) {
                super.setSelectionInterval(-1, -1);
            }
        });
        SwingUtils.disableSelection(this);
        BookmarkManager.get().addListener(this::update);
    }

    public void update() {
        LazyListModel<String> lm = new LazyListModel<>();
        LazyListModel<InstrEntry> clm = (LazyListModel<InstrEntry>) codeList.getModel();
        for (int i = 0; i < clm.getSize(); i++) {
            InstrEntry entry = clm.getElementAt(i);
            lm.addElement(BookmarkManager.get().isBookmarked(entry.getInstr()) ? MARKED : EMPTY);
        }
        setModel(lm);
    }
}
