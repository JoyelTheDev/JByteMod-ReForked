package dev.joyel.theme.ui;

import dev.joyel.theme.JByteTheme;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

public final class ThemePreviewPanel extends JPanel {
    private final JTextPane textPane;

    private static final Object[][] PREVIEW = {
            {"keyword", "invokevirtual"}, {"plain", " "},
            {"type", "java/io/PrintStream"}, {"plain", "."},
            {"method", "println"}, {"plain", "("},
            {"descriptor", "Ljava/lang/String;"}, {"plain", ")\n"},

            {"keyword", "ldc"}, {"plain", " "},
            {"string", "\"Hello, World!\""}, {"plain", "\n"},

            {"keyword", "bipush"}, {"plain", " "},
            {"number", "42"}, {"plain", "\n"},

            {"keyword", "ifeq"}, {"plain", " "},
            {"label", "L0"}, {"plain", "\n"},

            {"keyword", "getstatic"}, {"plain", " "},
            {"type", "java/lang/System"}, {"plain", "."},
            {"field", "out"}, {"plain", " "},
            {"descriptor", "Ljava/io/PrintStream;"}, {"plain", "\n"},

            {"label", "L0:"}, {"plain", "  "},
            {"keyword", "return"}, {"plain", "\n"},
    };

    public ThemePreviewPanel() {
        super(new BorderLayout());
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(textPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void refresh(JByteTheme theme) {
        Color backgroundColor = theme.getColor("editor.background");
        textPane.setBackground(backgroundColor);

        StyledDocument document = textPane.getStyledDocument();
        try {
            document.remove(0, document.getLength());
        } catch (BadLocationException ignored) {
        }

        for (Object[] pair : PREVIEW) {
            String role = (String) pair[0];
            String text = (String) pair[1];
            Style style = textPane.addStyle(null, null);
            StyleConstants.setForeground(style, resolveColor(role, theme));
            StyleConstants.setBackground(style, backgroundColor);
            try {
                document.insertString(document.getLength(), text, style);
            } catch (BadLocationException ignored) {
            }
        }
    }

    private Color resolveColor(String role, JByteTheme theme) {
        if (role.equals("keyword")) {
            return theme.getColor("syntax.keyword");
        }
        if (role.equals("string")) {
            return theme.getColor("syntax.string");
        }
        if (role.equals("number")) {
            return theme.getColor("syntax.number");
        }
        if (role.equals("label")) {
            return theme.getColor("syntax.label");
        }
        if (role.equals("type")) {
            return theme.getColor("syntax.type");
        }
        if (role.equals("method")) {
            return theme.getColor("syntax.method");
        }
        if (role.equals("field")) {
            return theme.getColor("syntax.field");
        }
        if (role.equals("param")) {
            return theme.getColor("syntax.param");
        }
        if (role.equals("descriptor")) {
            return theme.getColor("syntax.label");
        }
        return theme.getColor("editor.foreground");
    }
}