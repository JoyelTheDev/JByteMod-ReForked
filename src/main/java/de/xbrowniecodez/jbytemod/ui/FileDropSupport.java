package de.xbrowniecodez.jbytemod.ui;

import de.xbrowniecodez.jbytemod.Main;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class FileDropSupport {
    private static final String[] EXTENSIONS = {".jar", ".class", ".apk"};

    private final Consumer<File> loader;
    private final DropTargetListener dropListener = new DropListener();
    private final ContainerListener containerListener = new ContainerListener() {
        @Override
        public void componentAdded(ContainerEvent e) {
            installRecursive(e.getChild());
        }

        @Override
        public void componentRemoved(ContainerEvent e) {
        }
    };

    private FileDropSupport(Consumer<File> loader) {
        this.loader = loader;
    }

    public static void install(Window window, Consumer<File> loader) {
        FileDropSupport support = new FileDropSupport(loader);
        support.installRecursive(window);
    }

    public static boolean isSupported(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private void installRecursive(Component component) {
        if (component instanceof JComponent || component instanceof Window) {
            component.setDropTarget(new DropTarget(component, DnDConstants.ACTION_COPY, dropListener, true));
        }
        if (component instanceof Container) {
            Container container = (Container) component;
            container.removeContainerListener(containerListener);
            container.addContainerListener(containerListener);
            for (Component child : container.getComponents()) {
                installRecursive(child);
            }
        }
        if (component instanceof JFrame) {
            installRecursive(((JFrame) component).getRootPane());
        }
    }

    private final class DropListener extends DropTargetAdapter {
        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
            check(dtde);
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde) {
            check(dtde);
        }

        @Override
        public void dropActionChanged(DropTargetDragEvent dtde) {
            check(dtde);
        }

        private void check(DropTargetDragEvent dtde) {
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            } else {
                dtde.rejectDrag();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void drop(DropTargetDropEvent dtde) {
            if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.rejectDrop();
                return;
            }
            List<File> files;
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            } catch (Exception e) {
                dtde.dropComplete(false);
                return;
            }
            dtde.dropComplete(true);

            File target = null;
            for (File file : files) {
                if (isSupported(file)) {
                    target = file;
                    break;
                }
            }
            final File chosen = target;
            SwingUtilities.invokeLater(() -> {
                if (chosen == null) {
                    JOptionPane.showMessageDialog(null,
                            "Unsupported file. Drop a .jar, .class or .apk file.",
                            "Drag and drop", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Main.INSTANCE.getLogger().log("Opening dropped file: " + chosen.getAbsolutePath());
                loader.accept(chosen);
            });
        }
    }
}
