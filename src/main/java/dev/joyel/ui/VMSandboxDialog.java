package dev.joyel.ui;

import de.xbrowniecodez.jbytemod.JByteMod;
import dev.joyel.vm.VM;
import dev.joyel.vm.VMRunner;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class VMSandboxDialog extends JDialog {

    private final JByteMod jbm;

    private final JTextField ownerField;
    private final JTextField nameField;
    private final JTextField descField;
    private final JTextArea argsArea;
    private final JTextArea logArea;
    private final JCheckBox noInitCheckBox;
    private final JList<String> methodList;
    private final DefaultListModel<String> methodListModel;
    private final JButton runButton;

    public VMSandboxDialog(JByteMod jbm) {
        super(jbm, "VM Sandbox", true);
        this.jbm = jbm;

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(200, 150, 760, 620);

        JPanel contentPane = new JPanel(new BorderLayout(6, 6));
        contentPane.setBorder(new EmptyBorder(8, 8, 8, 8));
        setContentPane(contentPane);

        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        contentPane.add(topPanel, BorderLayout.NORTH);

        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 4, 4));
        inputPanel.setBorder(new TitledBorder("Target Method"));

        inputPanel.add(new JLabel("Owner (e.g. com/example/Foo):"));
        ownerField = new JTextField();
        inputPanel.add(ownerField);

        inputPanel.add(new JLabel("Method Name:"));
        nameField = new JTextField();
        inputPanel.add(nameField);

        inputPanel.add(new JLabel("Descriptor (e.g. (Ljava/lang/String;I)Ljava/lang/String;):"));
        descField = new JTextField();
        inputPanel.add(descField);

        inputPanel.add(new JLabel("Arguments (one per line, e.g. \"hello\" or 42):"));
        argsArea = new JTextArea(2, 20);
        argsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        inputPanel.add(new JScrollPane(argsArea));

        topPanel.add(inputPanel, BorderLayout.CENTER);

        JPanel optionAndButtons = new JPanel(new BorderLayout(4, 4));

        noInitCheckBox = new JCheckBox("Skip static initializers (<clinit>)", true);
        optionAndButtons.add(noInitCheckBox, BorderLayout.NORTH);

        JPanel buttonRow = new JPanel(new GridLayout(1, 4, 6, 0));
        buttonRow.setBorder(new EmptyBorder(4, 0, 0, 0));

        JButton scanButton = new JButton("Scan Static Methods");
        runButton = new JButton("Run in Sandbox");
        JButton clearButton = new JButton("Clear Log");
        JButton closeButton = new JButton("Close");

        buttonRow.add(scanButton);
        buttonRow.add(runButton);
        buttonRow.add(clearButton);
        buttonRow.add(closeButton);
        optionAndButtons.add(buttonRow, BorderLayout.SOUTH);
        topPanel.add(optionAndButtons, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.35);
        contentPane.add(splitPane, BorderLayout.CENTER);

        methodListModel = new DefaultListModel<>();
        methodList = new JList<>(methodListModel);
        methodList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        methodList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane methodScroll = new JScrollPane(methodList);
        methodScroll.setBorder(new TitledBorder("Static Methods (double-click to fill fields)"));
        splitPane.setTopComponent(methodScroll);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Sandbox Log"));
        splitPane.setBottomComponent(logScroll);

        methodList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = methodList.getSelectedValue();
                    if (selected != null) {
                        fillFromEntry(selected);
                    }
                }
            }
        });

        scanButton.addActionListener(e -> onScan());
        runButton.addActionListener(e -> onRun());
        clearButton.addActionListener(e -> logArea.setText(""));
        closeButton.addActionListener(e -> dispose());
    }

    private void onScan() {
        Map<String, ClassNode> classes = getClasses();
        if (classes == null) return;

        log("Scanning for static methods...");
        methodListModel.clear();

        List<String> entries = new ArrayList<>();
        for (ClassNode cn : classes.values()) {
            for (MethodNode mn : cn.methods) {
                if ((mn.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0
                        && !mn.name.equals("<clinit>")) {
                    entries.add(cn.name + "." + mn.name + mn.desc);
                }
            }
        }

        Collections.sort(entries);
        if (entries.isEmpty()) {
            log("No static methods found.");
        } else {
            for (String e : entries) {
                methodListModel.addElement(e);
            }
            log("Found " + entries.size() + " static method(s). Double-click one to fill the fields.");
        }
    }

    private void onRun() {
        Map<String, ClassNode> classes = getClasses();
        if (classes == null) return;

        String owner = ownerField.getText().trim();
        String name  = nameField.getText().trim();
        String desc  = descField.getText().trim();

        if (owner.isEmpty() || name.isEmpty() || desc.isEmpty()) {
            log("ERROR: Owner, method name, and descriptor must all be filled.");
            return;
        }

        Object[] parsedArgs;
        try {
            parsedArgs = parseArgs(argsArea.getText(), desc);
        } catch (Exception ex) {
            log("ERROR: Could not parse arguments: " + ex.getMessage());
            return;
        }

        boolean noInit = noInitCheckBox.isSelected();
        runButton.setEnabled(false);

        log("---");
        log("Target : " + owner + "." + name + desc);
        log("Args   : " + argsToString(parsedArgs));
        log("NoInit : " + noInit);
        log("Building sandbox VM...");

        SwingWorker<String, String> worker = new SwingWorker<String, String>() {

            @Override
            protected String doInBackground() {
                try {
                    VMRunner runner = new VMRunner(classes);
                    VM vm = runner.buildVM(noInit);
                    publish("VM loaded " + vm.loaded.size() + " class(es). Invoking...");
                    Object result = runner.invokeStatic(vm, owner, name, desc, parsedArgs);
                    return "Result: " + resultToString(result);
                } catch (SecurityException se) {
                    return "BLOCKED: " + se.getMessage();
                } catch (ClassNotFoundException cnf) {
                    return "ERROR (class not found): " + cnf.getMessage();
                } catch (NoSuchMethodException nsm) {
                    return "ERROR (method not found): " + nsm.getMessage();
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    return "ERROR (runtime exception in target): " + (cause != null ? cause : ite);
                } catch (Throwable t) {
                    return "ERROR: " + t.getClass().getSimpleName() + ": " + t.getMessage();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) log(msg);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    log(get());
                } catch (Exception ex) {
                    log("ERROR: " + ex.getMessage());
                }
                log("---");
            }
        };

        worker.execute();
    }

    private void fillFromEntry(String entry) {
        int dot   = entry.indexOf('.');
        int paren = entry.indexOf('(');
        if (dot < 0 || paren < 0 || paren < dot) return;
        ownerField.setText(entry.substring(0, dot));
        nameField.setText(entry.substring(dot + 1, paren));
        descField.setText(entry.substring(paren));
        log("Filled from: " + entry);
    }

    private Object[] parseArgs(String raw, String desc) {
        org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(desc);
        if (argTypes.length == 0) return new Object[0];

        String[] lines = raw.isEmpty()
                ? new String[0]
                : raw.split("\\r?\\n", -1);

        if (lines.length < argTypes.length) {
            throw new IllegalArgumentException(
                    "Descriptor requires " + argTypes.length + " arg(s) but only " + lines.length + " line(s) provided.");
        }

        Object[] args = new Object[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            args[i] = parseSingleArg(lines[i].trim(), argTypes[i]);
        }
        return args;
    }

    private Object parseSingleArg(String token, org.objectweb.asm.Type type) {
        switch (type.getSort()) {
            case org.objectweb.asm.Type.INT:     return Integer.parseInt(token);
            case org.objectweb.asm.Type.LONG:
                return Long.parseLong(token.endsWith("L") || token.endsWith("l")
                        ? token.substring(0, token.length() - 1) : token);
            case org.objectweb.asm.Type.FLOAT:   return Float.parseFloat(token);
            case org.objectweb.asm.Type.DOUBLE:  return Double.parseDouble(token);
            case org.objectweb.asm.Type.BOOLEAN: return Boolean.parseBoolean(token);
            case org.objectweb.asm.Type.OBJECT:
                if (type.getClassName().equals("java.lang.String")) {
                    if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
                        return token.substring(1, token.length() - 1);
                    }
                    return token;
                }
                if (token.equalsIgnoreCase("null")) return null;
                return token;
            default:
                return token;
        }
    }

    private String resultToString(Object result) {
        if (result == null) return "null";
        if (result instanceof String) return "\"" + result + "\"";
        if (result instanceof byte[]) return "byte[" + ((byte[]) result).length + "]";
        if (result instanceof char[]) return "new String(result) = \"" + new String((char[]) result) + "\"";
        return result.getClass().getSimpleName() + " = " + result;
    }

    private String argsToString(Object[] args) {
        if (args == null || args.length == 0) return "(none)";
        return Arrays.stream(args)
                .map(a -> a == null ? "null" : a.toString())
                .collect(Collectors.joining(", "));
    }

    private Map<String, ClassNode> getClasses() {
        if (jbm.getJarArchive() == null || jbm.getJarArchive().getClasses() == null) {
            log("ERROR: No file loaded.");
            return null;
        }
        return jbm.getJarArchive().getClasses();
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void open(JByteMod jbm) {
        VMSandboxDialog dialog = new VMSandboxDialog(jbm);
        dialog.setLocationRelativeTo(jbm);
        dialog.setVisible(true);
    }
}
