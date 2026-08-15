package dev.joyel.hierarchy;

import de.xbrowniecodez.jbytemod.JByteMod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;

public final class HierarchyViewerFrame extends JFrame {

    private static final Color COL_ABSTRACT  = new Color(0x569cd6);
    private static final Color COL_INTERFACE = new Color(0x4ec9b0);
    private static final Color COL_CONCRETE  = new Color(0xdcdcaa);
    private static final Color COL_OVERRIDE  = new Color(0x9cdcfe);
    private static final Color COL_ROOT_NODE = new Color(0xce9178);

    private final JByteMod jbm;
    private final JTree classTree;
    private final JList<OverrideEntry> overrideList;
    private final DefaultListModel<OverrideEntry> overrideModel;
    private final JLabel statusLabel;
    private final JLabel methodLabel;

    public HierarchyViewerFrame(JByteMod jbm, ClassNode focusClass, MethodNode focusMethod) {
        super(buildTitle(focusClass, focusMethod));
        this.jbm = jbm;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(860, 600);
        setLocationRelativeTo(jbm);

        overrideModel = new DefaultListModel<OverrideEntry>();
        overrideList  = buildOverrideList();
        classTree     = buildClassTree();
        statusLabel   = new JLabel(" ");
        statusLabel.setBorder(new EmptyBorder(3, 4, 3, 4));
        methodLabel   = new JLabel(" ");
        methodLabel.setBorder(new EmptyBorder(3, 4, 3, 4));
        methodLabel.setForeground(Color.GRAY);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapScroll(classTree,   "Class Hierarchy"),
                wrapScroll(overrideList, "Override Group"));
        split.setDividerLocation(440);
        split.setResizeWeight(0.55);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.WEST);
        bottom.add(methodLabel, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setBorder(new EmptyBorder(6, 6, 4, 6));
        root.add(split,   BorderLayout.CENTER);
        root.add(bottom,  BorderLayout.SOUTH);
        setContentPane(root);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        populate(focusClass, focusMethod);
    }

    public static void open(JByteMod jbm, ClassNode cn, MethodNode mn) {
        if (!HierarchyManager.getInstance().isReady()) {
            JOptionPane.showMessageDialog(jbm,
                    "Hierarchy index is not ready yet.\nPlease wait a moment and try again.",
                    "Hierarchy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new HierarchyViewerFrame(jbm, cn, mn).setVisible(true);
            }
        });
    }

    private void populate(ClassNode focusClass, MethodNode focusMethod) {
        HierarchyIndex index = HierarchyManager.getInstance().getIndex();
        if (index == null) {
            statusLabel.setText("Hierarchy index not available.");
            return;
        }

        populateClassTree(index, focusClass);

        if (focusMethod != null && canParticipate(focusMethod)) {
            populateOverrides(index, focusClass, focusMethod);
            methodLabel.setText(focusMethod.name + focusMethod.desc + "  ");
        } else {
            methodLabel.setText(focusMethod != null ? focusMethod.name + " (no override tracking)  " : " ");
        }
    }

    private void populateClassTree(HierarchyIndex index, ClassNode focusClass) {
        ClassHierarchyNode focusNode = index.getClassNode(focusClass.name);
        if (focusNode == null) {
            statusLabel.setText("Class not in index: " + focusClass.name);
            return;
        }

        DefaultMutableTreeNode treeRoot = buildSuperChain(index, focusNode);
        DefaultMutableTreeNode focusTreeNode = findNode(treeRoot, focusClass.name);
        if (focusTreeNode == null) focusTreeNode = treeRoot;

        appendInheritors(focusTreeNode, focusNode, 0, 3);

        classTree.setModel(new DefaultTreeModel(treeRoot));
        expandAll(classTree, new TreePath(treeRoot));

        TreePath focusPath = findPath(classTree, focusClass.name);
        if (focusPath != null) {
            classTree.setSelectionPath(focusPath);
            classTree.scrollPathToVisible(focusPath);
        }

        int totalInheritors = focusNode.getInheritors().size();
        statusLabel.setText(" " + shortName(focusClass.name)
                + "  |  " + totalInheritors + " direct subclass" + (totalInheritors == 1 ? "" : "es"));
    }

    private DefaultMutableTreeNode buildSuperChain(HierarchyIndex index, ClassHierarchyNode node) {
        java.util.List<ClassHierarchyNode> chain = index.getSuperChain(node.getClassNode().name);
        java.util.Collections.reverse(chain);

        DefaultMutableTreeNode parent = null;
        DefaultMutableTreeNode first  = null;
        for (ClassHierarchyNode n : chain) {
            DefaultMutableTreeNode tn = new DefaultMutableTreeNode(new ClassEntry(n.getClassNode()));
            if (first == null) first = tn;
            if (parent != null) parent.add(tn);
            parent = tn;
        }

        if (node.getSuperClass() == null && first == null) {
            first = new DefaultMutableTreeNode(new ClassEntry(node.getClassNode()));
        }

        if (first == null) {
            first = new DefaultMutableTreeNode(new ClassEntry(node.getClassNode()));
        }
        return first;
    }

    private void appendInheritors(DefaultMutableTreeNode parentNode, ClassHierarchyNode parentHNode,
                                  int depth, int maxDepth) {
        if (depth >= maxDepth) return;
        for (ClassHierarchyNode sub : parentHNode.getInheritors()) {
            DefaultMutableTreeNode child = new DefaultMutableTreeNode(new ClassEntry(sub.getClassNode()));
            parentNode.add(child);
            appendInheritors(child, sub, depth + 1, maxDepth);
        }
    }

    private void populateOverrides(HierarchyIndex index, ClassNode owner, MethodNode method) {
        overrideModel.clear();
        MethodHierarchyGroup group = index.getMethodGroup(owner.name, method.name, method.desc);
        if (group == null || group.size() <= 1) {
            overrideModel.addElement(new OverrideEntry(owner, method, true));
            return;
        }
        for (MethodHierarchyGroup.Entry e : group.getMembers()) {
            boolean isRoot = e.getOwner().name.equals(owner.name);
            overrideModel.addElement(new OverrideEntry(e.getOwner(), e.getMethod(), isRoot));
        }
    }

    private JTree buildClassTree() {
        JTree tree = new JTree((DefaultTreeModel) null);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new ClassTreeRenderer());
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() < 2) return;
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null) return;
                Object last = path.getLastPathComponent();
                if (!(last instanceof DefaultMutableTreeNode)) return;
                Object userObj = ((DefaultMutableTreeNode) last).getUserObject();
                if (userObj instanceof ClassEntry) {
                    navigateToClass(((ClassEntry) userObj).cn);
                }
            }
        });
        return tree;
    }

    private JList<OverrideEntry> buildOverrideList() {
        JList<OverrideEntry> list = new JList<OverrideEntry>(overrideModel);
        list.setCellRenderer(new OverrideListRenderer());
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() < 2) return;
                OverrideEntry entry = list.getSelectedValue();
                if (entry != null) navigateToMethod(entry.owner, entry.method);
            }
        });
        return list;
    }

    private void navigateToClass(ClassNode cn) {
        if (cn == null) return;
        ClassNode live = findLive(cn.name);
        if (live != null) jbm.selectMethod(live, null);
    }

    private void navigateToMethod(ClassNode owner, MethodNode method) {
        if (owner == null || method == null) return;
        ClassNode live = findLive(owner.name);
        if (live == null) return;
        for (MethodNode mn : live.methods) {
            if (mn.name.equals(method.name) && mn.desc.equals(method.desc)) {
                jbm.selectMethod(live, mn);
                return;
            }
        }
        jbm.selectMethod(live, null);
    }

    private ClassNode findLive(String internalName) {
        if (jbm.getJarArchive() == null || jbm.getJarArchive().getClasses() == null) return null;
        return jbm.getJarArchive().getClasses().get(internalName);
    }

    private static DefaultMutableTreeNode findNode(DefaultMutableTreeNode root, String name) {
        if (root.getUserObject() instanceof ClassEntry
                && ((ClassEntry) root.getUserObject()).cn.name.equals(name)) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            DefaultMutableTreeNode result = findNode(child, name);
            if (result != null) return result;
        }
        return null;
    }

    private static TreePath findPath(JTree tree, String name) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        if (model == null) return null;
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        DefaultMutableTreeNode found = findNode(root, name);
        if (found == null) return null;
        return new TreePath(model.getPathToRoot(found));
    }

    private static void expandAll(JTree tree, TreePath path) {
        tree.expandPath(path);
        Object node = path.getLastPathComponent();
        if (!(node instanceof DefaultMutableTreeNode)) return;
        DefaultMutableTreeNode tn = (DefaultMutableTreeNode) node;
        for (int i = 0; i < tn.getChildCount(); i++) {
            expandAll(tree, path.pathByAddingChild(tn.getChildAt(i)));
        }
    }

    private static JPanel wrapScroll(Component c, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(title));
        JScrollPane scroll = new JScrollPane(c);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static String shortName(String internalName) {
        int i = internalName.lastIndexOf('/');
        return i == -1 ? internalName : internalName.substring(i + 1);
    }

    private static String buildTitle(ClassNode cn, MethodNode mn) {
        if (mn != null) return "Hierarchy \u2013 " + shortName(cn.name) + "#" + mn.name;
        return "Hierarchy \u2013 " + shortName(cn.name);
    }

    private static boolean isAbstract(ClassNode cn) {
        return (cn.access & Opcodes.ACC_ABSTRACT) != 0;
    }

    private static boolean isInterface(ClassNode cn) {
        return (cn.access & Opcodes.ACC_INTERFACE) != 0;
    }

    private static boolean canParticipate(MethodNode mn) {
        boolean isInit    = mn.name.equals("<init>") || mn.name.equals("<clinit>");
        boolean isStatic  = (mn.access & Opcodes.ACC_STATIC)  != 0;
        boolean isPrivate = (mn.access & Opcodes.ACC_PRIVATE) != 0;
        return !isInit && !isStatic && !isPrivate;
    }

    private static final class ClassEntry {
        final ClassNode cn;
        ClassEntry(ClassNode cn) { this.cn = cn; }

        @Override
        public String toString() {
            String simple = shortName(cn.name);
            if (isInterface(cn)) return "\u00ab interface \u00bb " + simple;
            if (isAbstract(cn))  return "\u00ab abstract \u00bb " + simple;
            return simple;
        }
    }

    private static final class OverrideEntry {
        final ClassNode  owner;
        final MethodNode method;
        final boolean    isRoot;

        OverrideEntry(ClassNode owner, MethodNode method, boolean isRoot) {
            this.owner  = owner;
            this.method = method;
            this.isRoot = isRoot;
        }

        @Override
        public String toString() {
            String ownerSimple = shortName(owner.name);
            boolean isAbstractMethod = (method.access & Opcodes.ACC_ABSTRACT) != 0;
            String prefix = isAbstractMethod ? "abstract " : "";
            return ownerSimple + "." + prefix + method.name;
        }
    }

    private final class ClassTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setIcon(null);
            if (value instanceof DefaultMutableTreeNode) {
                Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObj instanceof ClassEntry) {
                    ClassEntry entry = (ClassEntry) userObj;
                    ClassNode cn = entry.cn;
                    boolean isFocus = cn.name.equals(
                            jbm.getCurrentNode() != null ? jbm.getCurrentNode().name : "");
                    if (!selected) {
                        if (isFocus)           setForeground(COL_ROOT_NODE);
                        else if (isInterface(cn)) setForeground(COL_INTERFACE);
                        else if (isAbstract(cn))  setForeground(COL_ABSTRACT);
                        else                      setForeground(COL_CONCRETE);
                    }
                }
            }
            return this;
        }
    }

    private static final class OverrideListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof OverrideEntry && !isSelected) {
                OverrideEntry e = (OverrideEntry) value;
                boolean isAbstractMethod = (e.method.access & Opcodes.ACC_ABSTRACT) != 0;
                if (e.isRoot)           setForeground(COL_ROOT_NODE);
                else if (isAbstractMethod) setForeground(COL_ABSTRACT);
                else                       setForeground(COL_OVERRIDE);
            }
            return this;
        }
    }
}
