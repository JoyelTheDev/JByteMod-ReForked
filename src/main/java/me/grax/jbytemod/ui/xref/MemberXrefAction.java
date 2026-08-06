package me.grax.jbytemod.ui.xref;

import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.xref.XrefEntry;
import me.grax.jbytemod.xref.XrefManager;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import java.util.List;

public final class MemberXrefAction {

    private MemberXrefAction() {
    }

    public static void showForMethod(JByteMod jbm, String owner, MethodNode mn) {
        if (!XrefManager.getInstance().isReady()) {
            JOptionPane.showMessageDialog(jbm,
                    "Xref index is not built yet.\nOpen a JAR first and wait for the index to finish.",
                    "Xrefs Not Ready", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<XrefEntry> entries = XrefManager.getInstance().getMemberRefs(owner, mn.name, mn.desc);
        String label = simpleOwner(owner) + "." + mn.name + mn.desc;

        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(jbm,
                    "No references found to: " + label,
                    "No Xrefs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        new XrefViewerFrame(jbm, label, entries).setVisible(true);
    }

    public static void showForField(JByteMod jbm, String owner, FieldNode fn) {
        if (!XrefManager.getInstance().isReady()) {
            JOptionPane.showMessageDialog(jbm,
                    "Xref index is not built yet.\nOpen a JAR first and wait for the index to finish.",
                    "Xrefs Not Ready", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<XrefEntry> entries = XrefManager.getInstance().getMemberRefs(owner, fn.name, fn.desc);
        String label = simpleOwner(owner) + "." + fn.name;

        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(jbm,
                    "No references found to: " + label,
                    "No Xrefs", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        new XrefViewerFrame(jbm, label, entries).setVisible(true);
    }

    private static String simpleOwner(String internal) {
        if (internal == null) return "?";
        int slash = internal.lastIndexOf('/');
        return slash == -1 ? internal : internal.substring(slash + 1);
    }
}
