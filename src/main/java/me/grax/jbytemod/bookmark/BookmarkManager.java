package me.grax.jbytemod.bookmark;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class BookmarkManager {

    private static final BookmarkManager INSTANCE = new BookmarkManager();

    public static BookmarkManager get() {
        return INSTANCE;
    }

    private final List<Bookmark> bookmarks = new ArrayList<>();
    private final IdentityHashMap<AbstractInsnNode, Bookmark> index = new IdentityHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private BookmarkManager() {}

    public void add(ClassNode cn, MethodNode mn, AbstractInsnNode ain, String label) {
        if (index.containsKey(ain)) return;
        Bookmark b = new Bookmark(cn, mn, ain, label);
        bookmarks.add(b);
        index.put(ain, b);
        notifyListeners();
    }

    public void remove(AbstractInsnNode ain) {
        Bookmark b = index.remove(ain);
        if (b != null) {
            bookmarks.remove(b);
            notifyListeners();
        }
    }

    public boolean isBookmarked(AbstractInsnNode ain) {
        return index.containsKey(ain);
    }

    public Bookmark getBookmark(AbstractInsnNode ain) {
        return index.get(ain);
    }

    public List<Bookmark> getAll() {
        return bookmarks;
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    private void notifyListeners() {
        for (Runnable r : listeners) r.run();
    }
}
