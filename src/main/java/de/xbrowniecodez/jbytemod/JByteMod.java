package de.xbrowniecodez.jbytemod;

import com.sun.tools.attach.VirtualMachine;
import dev.joyel.theme.ThemeManager;
import de.xbrowniecodez.jbytemod.ui.FileDropSupport;
import de.xbrowniecodez.jbytemod.utils.BytecodeUtils;
import de.xbrowniecodez.jbytemod.utils.Utils;
import de.xbrowniecodez.jbytemod.utils.update.objects.Version;
import lombok.Getter;
import lombok.Setter;
import de.xbrowniecodez.jbytemod.plugin.Plugin;
import de.xbrowniecodez.jbytemod.plugin.PluginManager;
import dev.joyel.hierarchy.HierarchyManager;
import me.grax.jbytemod.xref.XrefManager;
import me.grax.jbytemod.xref.XrefMap;
import me.grax.jbytemod.JarArchive;
import me.grax.jbytemod.res.LanguageRes;
import me.grax.jbytemod.res.Options;
import me.grax.jbytemod.ui.*;
import de.xbrowniecodez.jbytemod.ui.lists.LVPList;
import me.grax.jbytemod.ui.graph.ControlFlowPanel;
import me.grax.jbytemod.ui.lists.MyCodeList;
import dev.joyel.tutorial.TutorialDialog;
import de.xbrowniecodez.jbytemod.ui.lists.SearchList;
import de.xbrowniecodez.jbytemod.ui.lists.TCBList;
import me.grax.jbytemod.ui.tree.SortedTreeNode;
import me.grax.jbytemod.utils.ErrorDisplay;
import me.grax.jbytemod.utils.task.LoadTask;
import me.grax.jbytemod.utils.gui.LookUtils;
import dev.joyel.attach.RemoteJarArchive;
import me.grax.jbytemod.utils.task.AttachTask;
import me.grax.jbytemod.utils.task.RetransformTask;
import me.grax.jbytemod.utils.task.SaveTask;
import me.lpk.util.OpUtils;
import dev.joyel.ui.metrics.MetricsPanel;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.Locale;
import java.util.LinkedHashMap;

@Getter
@Setter
public class JByteMod extends JFrame {
    private final Version version = new Version(Utils.readPropertiesFile().getProperty("version"));
    private final String title = "JByteMod ReForked v" + version;
    private LanguageRes languageRes;
    private String lastEditFile = "";
    private HashMap<ClassNode, MethodNode> lastSelectedTreeEntries = new LinkedHashMap<>();
    private JarArchive jarArchive;
    private Instrumentation agentInstrumentation;
    private Options options;
    private ClassTree jarTree;
    private MyCodeList codeList;
    private PageEndPanel pageEndPanel;
    private SearchList searchList;
    private DecompilerPanel decompilerPanel;
    private ControlFlowPanel controlFlowPanel;
    private TCBList tcbList;
    private MyTabbedPane tabbedPane;
    private InfoPanel infoPanel;
    private MetricsPanel metricsPanel;
    private LVPList lvpList;
    private MyMenuBar myMenuBar;
    private MyToolBar toolBar;
    private ClassNode currentNode;
    private MethodNode currentMethod;
    private PluginManager pluginManager;
    private File filePath;

    public JByteMod(boolean agent) throws Exception {
        this.options = new Options();
        this.languageRes = new LanguageRes();
    }

    public void initializeFrame(boolean agent) {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
    @Override
    public void windowClosing(WindowEvent we) {
         handleWindowClosing(agent);
            }
        });

        setBounds(100, 100, 1280, 720);
        setTitle(title);
        try {
            java.net.URL iconUrl = getClass().getClassLoader().getResource("resources/logo.png");
            if (iconUrl != null) {
                setIconImage(javax.imageio.ImageIO.read(iconUrl));
            }
        } catch (Exception ignored) {}
        setJMenuBar(myMenuBar = new MyMenuBar(this, agent));
        jarTree = new ClassTree(this);
        JPanel contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(5, 5));
        setContentPane(contentPane);
        setTcbList(new TCBList());
        setLvpList(new LVPList());
        createSplitPane(contentPane, agent);
        contentPane.add(pageEndPanel = new PageEndPanel(), BorderLayout.PAGE_END);
        contentPane.add(toolBar = new MyToolBar(this), BorderLayout.PAGE_START);

        if (jarArchive != null) {
            refreshTree();
        }

        FileDropSupport.install(this, this::loadFile);
    }

    private void createSplitPane(Container container, boolean agent) {
        JPanel borderPanel = new JPanel();
        borderPanel.setBorder(null);
        borderPanel.setLayout(new GridLayout());
        JSplitPane splitPane = new MySplitPane(this, jarTree);
        JPanel b2 = new JPanel();
        b2.setBorder(new EmptyBorder(5, 0, 5, 0));
        b2.setLayout(new GridLayout());
        b2.add(splitPane);
        borderPanel.add(b2);
        container.add(borderPanel, BorderLayout.CENTER);
     }

    private void handleWindowClosing(boolean agent) {
        if (JOptionPane.showConfirmDialog(JByteMod.this, languageRes.getResource("exit_warn"), languageRes.getResource("is_sure"),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (jarArchive instanceof RemoteJarArchive remoteArchive) {
                try {
                    remoteArchive.close();
                } catch (Exception ignored) {
                }
            }
            if (agent) {
                dispose();
            } else {
                Main.INSTANCE.getDiscord().shutdown();
                Runtime.getRuntime().exit(0);
            }
        }
    }

    public void applyChangesAgent() {
        if (jarArchive instanceof RemoteJarArchive) {
            new RetransformTask(this, null, jarArchive).execute();
            return;
        }
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        new RetransformTask(this, agentInstrumentation, jarArchive).execute();
    }

    public void attachTo(VirtualMachine vm) throws Exception {
        new AttachTask(this, vm).execute();
    }

    /**
     * Load .jar, .class or .apk file
     */
    public void loadFile(File input) {
        String ap = input.getAbsolutePath().toLowerCase(Locale.ROOT);

        try {
            if (ap.endsWith(".jar") || ap.endsWith(".apk")) {
                loadZipFile(input);
            } else if (ap.endsWith(".class")) {
                loadClassFile(input);
                onFileLoaded(input);
            } else {
                displayJarWarning();
            }
        } catch (Throwable e) {
            Main.INSTANCE.getLogger().errNotification("Failed to load " + input.getName());
            new ErrorDisplay(e);
        }
    }

    private void loadZipFile(File input) {
        final JarArchive previous = jarArchive;
        final JarArchive[] loading = new JarArchive[1];
        loading[0] = new JarArchive(this, input, new LoadTask.Listener() {
            @Override
            public void onLoaded() {
                if (jarArchive == loading[0]) {
                    onFileLoaded(input);
                }
            }

            @Override
            public void onFailed(Throwable error) {
                if (jarArchive == loading[0] || jarArchive == null) {
                    jarArchive = previous;
                }
                Main.INSTANCE.getLogger().errNotification("Failed to load " + input.getName());
                new ErrorDisplay(error);
            }
        });
        jarArchive = loading[0];
    }

    private void loadClassFile(File input) throws Exception {
        jarArchive = new JarArchive(BytecodeUtils.getClassNodeFromBytes(Files.readAllBytes(input.toPath())));
        refreshTree();
    }

    private void onFileLoaded(File input) {
        this.filePath = input;
        setTitleSuffix(input.getName());
        RecentFiles.getInstance().add(input);
        buildIndexes();
        notifyPlugins();
        Main.INSTANCE.getLogger().successNotification("Loaded " + input.getName());
    }

    public void buildIndexes() {
        XrefManager.getInstance().clear();
        HierarchyManager.getInstance().clear();
        Main.INSTANCE.getLogger().log("Building Xref index...");
        XrefManager.getInstance().buildAsync(jarArchive, () -> {
            XrefMap map = XrefManager.getInstance().getCurrentMap();
            if (map != null) {
                Main.INSTANCE.getLogger().log("Xref index built (" + map.getAllMemberRefs().size()
                        + " member refs, " + map.getAllClassRefs().size() + " class refs).");
            }
        });
        Main.INSTANCE.getLogger().log("Building Hierarchy index...");
        HierarchyManager.getInstance().buildAsync(jarArchive, () ->
                Main.INSTANCE.getLogger().log("Hierarchy index built."));
    }

    private void displayJarWarning() {
        new ErrorDisplay(new UnsupportedOperationException(languageRes.getResource("jar_warn")));
    }

    private void notifyPlugins() {
        for (Plugin p : pluginManager.getPlugins()) {
            try {
                p.loadFile(jarArchive.getClasses());
            } catch (Throwable t) {
                Main.INSTANCE.getLogger().err("Plugin failed while handling the loaded file: " + t);
            }
        }
    }

    public void refreshAgentClasses() {
        if (jarArchive instanceof RemoteJarArchive remoteArchive) {
            pageEndPanel.setValue(0);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    remoteArchive.refresh(progress -> pageEndPanel.setValue(Math.min(progress, 99)));
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        refreshTree();
                        pageEndPanel.setValue(100);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException exception) {
                        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                        new ErrorDisplay(cause);
                    }
                }
            }.execute();
            return;
        }
        if (agentInstrumentation == null) {
            throw new RuntimeException();
        }
        this.refreshTree();
    }

    public void connectToAgent(RemoteJarArchive archive) {
        if (jarArchive instanceof RemoteJarArchive previousArchive && previousArchive != archive) {
            try {
                previousArchive.close();
            } catch (Exception ignored) {
            }
        }
        jarArchive = archive;
        filePath = null;
        lastEditFile = "attached process";
        lastSelectedTreeEntries.clear();
        installAgentChrome(true);
        setTitleSuffix("PID " + archive.getProcessId());
        refreshTree();
        revalidate();
        repaint();
    }

    public void terminateAttachedJvm() {
        if (!(jarArchive instanceof RemoteJarArchive remoteArchive)) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Terminate the connected JVM? This will immediately stop the target application.",
                "Terminate JVM",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        runRemoteSessionEnd(remoteArchive, true, "terminated process snapshot", "Target terminated");
    }

    public void detachAttachedJvm() {
        if (!(jarArchive instanceof RemoteJarArchive remoteArchive)) {
            return;
        }
        runRemoteSessionEnd(remoteArchive, false, "detached process snapshot", "Detached snapshot");
    }

    private void runRemoteSessionEnd(RemoteJarArchive remoteArchive, boolean terminate, String editFile, String titleSuffix) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (terminate) {
                    remoteArchive.terminate();
                } else {
                    remoteArchive.close();
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeAttachedJvmSession(remoteArchive, editFile, titleSuffix);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    new ErrorDisplay(cause);
                }
            }
        }.execute();
    }

    private void completeAttachedJvmSession(RemoteJarArchive remoteArchive, String editFile, String titleSuffix) {
        if (jarArchive != remoteArchive) {
            return;
        }
        JarArchive snapshot = new JarArchive(remoteArchive.getClasses(), new HashMap<>());
        snapshot.setJarManifest(remoteArchive.getJarManifest());
        jarArchive = snapshot;
        lastEditFile = editFile;
        installAgentChrome(false);
        setTitleSuffix(titleSuffix);
        notifyPlugins();
        revalidate();
        repaint();
    }

    private void installAgentChrome(boolean agentMode) {
        setJMenuBar(myMenuBar = new MyMenuBar(this, agentMode));
        if (pluginManager != null) {
            myMenuBar.addPluginMenu(pluginManager.getPlugins());
        }
        Container contentPane = getContentPane();
        if (toolBar != null) {
            contentPane.remove(toolBar);
        }
        contentPane.add(toolBar = new MyToolBar(this), BorderLayout.PAGE_START);
    }

    public void showWorkspace() {
        if (tabbedPane != null && tabbedPane.getTabCount() > 0 && tabbedPane.getSelectedIndex() < 0) {
            tabbedPane.setSelectedIndex(0);
        }
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    public void refreshTree() {
        Main.INSTANCE.getLogger().log("Building tree..");
        this.jarTree.refreshTree(jarArchive);
        if (jarArchive != null) {
            showWorkspace();
        }
        if (pageEndPanel != null && jarArchive != null && jarArchive.getClasses() != null) {
            pageEndPanel.setClassCount(jarArchive.getClasses().size());
        }
    }

    public void saveFile(File output) {
        try {
            new SaveTask(this, output, jarArchive).execute();
        } catch (Throwable t) {
            new ErrorDisplay(t);
        }
    }

    public void selectClass(ClassNode cn) {
        if (Main.INSTANCE.getJByteMod().getOptions().get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        this.currentNode = cn;
        this.currentMethod = null;
        if (pageEndPanel != null) pageEndPanel.setSelection(cn, null);
        infoPanel.selectClass(cn);
        codeList.loadFields(cn);
        tabbedPane.selectClass(cn);
        if (metricsPanel != null) metricsPanel.showClassMetrics(cn);
        lastSelectedTreeEntries.put(cn, null);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    private boolean selectEntry(MethodNode mn, DefaultTreeModel tm, SortedTreeNode node) {
        for (int i = 0; i < tm.getChildCount(node); i++) {
            SortedTreeNode child = (SortedTreeNode) tm.getChild(node, i);
            if (child.getMethodNode() != null && child.getMethodNode().equals(mn)) {
                TreePath tp = new TreePath(tm.getPathToRoot(child));
                jarTree.setSelectionPath(tp);
                jarTree.scrollPathToVisible(tp);
                return true;
            }
            if (!child.isLeaf()) {
                if (selectEntry(mn, tm, child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void selectMethod(ClassNode cn, MethodNode mn) {
        if (cn == null) {
            return;
        }
        if (mn == null) {
            selectClass(cn);
            return;
        }
        if (Main.INSTANCE.getJByteMod().getOptions().get("select_code_tab").getBoolean()) {
            tabbedPane.setSelectedIndex(0);
        }
        OpUtils.clearLabelCache();
        this.currentNode = cn;
        this.currentMethod = mn;
        if (pageEndPanel != null) pageEndPanel.setSelection(cn, mn);
        infoPanel.selectMethod(cn, mn);
        if (!codeList.loadInstructions(mn)) {
            codeList.setSelectedIndex(-1);
        }
        tcbList.addNodes(cn, mn);
        lvpList.addNodes(cn, mn);
        controlFlowPanel.setMethodNode(mn);
        decompilerPanel.setText("");
        tabbedPane.selectMethod(cn, mn);
        if (metricsPanel != null) metricsPanel.showMethodMetrics(cn, mn);
        lastSelectedTreeEntries.put(cn, mn);
        if (lastSelectedTreeEntries.size() > 5) {
            lastSelectedTreeEntries.remove(lastSelectedTreeEntries.keySet().iterator().next());
        }
    }

    public void setMetricsPanel(MetricsPanel p) { this.metricsPanel = p; }
    public MetricsPanel getMetricsPanel() { return metricsPanel; }
    public me.grax.jbytemod.ui.MyTabbedPane getTabbedPane() { return tabbedPane; }
    public me.grax.jbytemod.ui.MyMenuBar getMyMenuBar() { return myMenuBar; }
    public me.grax.jbytemod.ui.PageEndPanel getPageEndPanel() { return pageEndPanel; }
    public me.grax.jbytemod.ui.lists.MyCodeList getCodeList() { return codeList; }
    public me.grax.jbytemod.ui.ClassTree getJarTree() { return jarTree; }
    public de.xbrowniecodez.jbytemod.ui.lists.SearchList getSearchList() { return searchList; }
    public me.grax.jbytemod.res.Options getOptions() { return options; }
    public me.grax.jbytemod.res.LanguageRes getLanguageRes() { return languageRes; }
    public JarArchive getJarArchive() { return jarArchive; }
    public ClassNode getCurrentNode() { return currentNode; }
    public MethodNode getCurrentMethod() { return currentMethod; }

    private void setTitleSuffix(String suffix) {
        this.setTitle(title + " - " + suffix);
    }

    @Override
    public void setVisible(boolean b) {
        LookUtils.setTheme();
        this.initializeFrame(false);
        this.setPluginManager(new PluginManager(this));
        this.myMenuBar.addPluginMenu(pluginManager.getPlugins());
        restoreSavedTheme();
        super.setVisible(b);
        TutorialDialog.openIfFirstTime(this);
    }

    private void restoreSavedTheme() {
        File prefsFile = new File(
                System.getProperty("user.home"), ".jbytemod" + File.separator + "theme.prefs");
        if (!prefsFile.exists()) {
            ThemeManager.getInstance().applyTheme(ThemeManager.getInstance().getThemes().get(0));
            return;
        }
        try {
            byte[] bytes;
            try (FileInputStream fis = new FileInputStream(prefsFile)) {
                bytes = new byte[(int) prefsFile.length()];
                fis.read(bytes);
            }
            String content = new String(bytes, StandardCharsets.UTF_8).trim();
            if (content.startsWith("active_theme=")) {
                ThemeManager.getInstance().loadActiveThemeByName(content.substring("active_theme=".length()));
            }
        } catch (IOException ignored) {
            ThemeManager.getInstance().applyTheme(ThemeManager.getInstance().getThemes().get(0));
        }
    }

    public void treeSelection(ClassNode cn, MethodNode mn) {
        new Thread(() -> {
            DefaultTreeModel tm = (DefaultTreeModel) jarTree.getModel();
            if (this.selectEntry(mn, tm, (SortedTreeNode) tm.getRoot())) {
                SwingUtilities.invokeLater(() -> jarTree.repaint());
            }
        }).start();
    }
}
