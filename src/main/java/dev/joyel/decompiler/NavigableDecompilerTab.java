package dev.joyel.decompiler;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import de.xbrowniecodez.jbytemod.decompiler.ASMifierDecompiler;
import de.xbrowniecodez.jbytemod.decompiler.JDCoreDecompiler;
import de.xbrowniecodez.jbytemod.decompiler.VineflowerDecompiler;
import me.grax.jbytemod.decompiler.CFRDecompiler;
import me.grax.jbytemod.decompiler.Decompiler;
import me.grax.jbytemod.decompiler.Decompilers;
import me.grax.jbytemod.decompiler.KoffeeDecompiler;
import me.grax.jbytemod.decompiler.ProcyonDecompiler;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public final class NavigableDecompilerTab extends JPanel {

    private final JByteMod jbm;
    private Decompilers decompiler = Decompilers.CFR;
    private final NavigableDecompilerPanel dp;
    private final JLabel label;
    private final JLabel hintLabel;

    public NavigableDecompilerTab(JByteMod jbm) {
        this.jbm = jbm;
        this.dp  = new NavigableDecompilerPanel(jbm);
        this.label = new JLabel(decompiler + " Decompiler");

        this.hintLabel = new JLabel("Ctrl+Click or double-click to navigate");
        hintLabel.setForeground(new Color(0x808080));
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));

        jbm.setDecompilerPanel(dp);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.setBorder(new EmptyBorder(1, 5, 5, 1));

        JPanel labelPanel = new JPanel(new GridLayout());
        labelPanel.add(label);

        JPanel rightPanel = new JPanel(new GridLayout(1, 5));
        for (int i = 0; i < 2; i++) rightPanel.add(new JPanel());

        rightPanel.add(hintLabel);

        JComboBox<Decompilers> decompilerCombo = new JComboBox<>(Decompilers.values());
        decompilerCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                NavigableDecompilerTab.this.decompiler = (Decompilers) decompilerCombo.getSelectedItem();
                label.setText(NavigableDecompilerTab.this.decompiler.getName()
                        + " " + NavigableDecompilerTab.this.decompiler.getVersion());
                decompile(Decompiler.last, Decompiler.lastMn, true);
            }
        });
        rightPanel.add(decompilerCombo);

        JButton reload = new JButton(Main.INSTANCE.getJByteMod().getLanguageRes().getResource("reload"));
        reload.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                decompile(Decompiler.last, Decompiler.lastMn, true);
            }
        });
        rightPanel.add(reload);

        labelPanel.add(rightPanel);
        topPanel.add(labelPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        RTextScrollPane scp = new RTextScrollPane(dp);
        scp.getVerticalScrollBar().setUnitIncrement(16);
        add(scp, BorderLayout.CENTER);
    }

    public void decompile(final ClassNode cn, final MethodNode mn, boolean deleteCache) {
        if (cn == null) return;

        dp.clearTokens();

        Decompiler d;
        switch (decompiler) {
            case PROCYON:    d = new ProcyonDecompiler(jbm, dp);    break;
            case VINEFLOWER: d = new VineflowerDecompiler(jbm, dp); break;
            case CFR:        d = new CFRDecompiler(jbm, dp);        break;
            case KOFFEE:     d = new KoffeeDecompiler(jbm, dp);     break;
            case JDCORE:     d = new JDCoreDecompiler(jbm, dp);     break;
            case ASMIFIER:   d = new ASMifierDecompiler(jbm, dp);   break;
            default:         d = new CFRDecompiler(jbm, dp);        break;
        }

        d.setNode(cn, mn);
        if (deleteCache) d.deleteCache();

        final Decompiler finalD = d;
        final String targetClass = cn.name;

        Thread runner = new Thread(new Runnable() {
            public void run() {
                finalD.run();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        String source = dp.getText();
                        if (source != null && !source.isEmpty()
                                && !source.startsWith("Loading")
                                && !source.startsWith("Failed")) {
                            dp.loadTokens(source, targetClass);
                        }
                    }
                });
            }
        }, "NavigableDecompiler-Worker");
        runner.setDaemon(true);
        runner.start();
    }
}
