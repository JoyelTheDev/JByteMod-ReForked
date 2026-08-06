package me.grax.jbytemod.ui.xref;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.xref.XrefEntry;
import me.grax.jbytemod.xref.XrefManager;
import org.objectweb.asm.tree.ClassNode;

import javax.swing.*;
import java.util.List;

public final class ClassXrefAction {

    private ClassXrefAction() {
    }

    public static void show(JByteMod jbm, ClassNode cn) {
        if (!XrefManager.getInstance().isReady()) {
            JOptionPane.showMessageDialog(jbm,
                    "Xref index is not built yet.\nOpen a JAR first and wait for the index to finish.",
                    "Xrefs Not Ready", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<XrefEntry> entries = XrefManager.getInstance().getClassRefs(cn.name);

        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(jbm,
                    "No references found to: " + cn.name,
                    "No Xrefs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        XrefViewerFrame frame = new XrefViewerFrame(jbm, cn.name, entries);
        frame.setVisible(true);
    }
}
