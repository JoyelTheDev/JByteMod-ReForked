package dev.joyel.theme;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JBytePalette {
    private static final Map<String, ThemeToken> TOKENS = new LinkedHashMap<String, ThemeToken>();
    private static final List<ThemeToken> ORDERED = new ArrayList<ThemeToken>();

    static {
        reg("editor.background",      "Background",           ThemeColorCategory.UI,        new Color(0x2b, 0x2b, 0x2b));
        reg("editor.foreground",      "Foreground Text",      ThemeColorCategory.UI,        new Color(0xd4, 0xd4, 0xd4));
        reg("editor.selection",       "Selection",            ThemeColorCategory.UI,        new Color(0x21, 0x42, 0x83));
        reg("editor.linehighlight",   "Line Highlight",       ThemeColorCategory.UI,        new Color(0x32, 0x32, 0x32));
        reg("editor.caret",           "Caret",                ThemeColorCategory.UI,        new Color(0xd4, 0xd4, 0xd4));
        reg("editor.border",          "Border",               ThemeColorCategory.UI,        new Color(0x44, 0x44, 0x44));

        reg("syntax.keyword",         "Keyword",              ThemeColorCategory.SYNTAX,    new Color(0x56, 0x9c, 0xd6));
        reg("syntax.string",          "String Literal",       ThemeColorCategory.SYNTAX,    new Color(0xce, 0x91, 0x78));
        reg("syntax.number",          "Number Literal",       ThemeColorCategory.SYNTAX,    new Color(0xb5, 0xce, 0xa8));
        reg("syntax.comment",         "Comment",              ThemeColorCategory.SYNTAX,    new Color(0x6a, 0x99, 0x55));
        reg("syntax.label",           "Label / Metadata",     ThemeColorCategory.SYNTAX,    new Color(0x9b, 0x9b, 0x9b));
        reg("syntax.type",            "Type Name",            ThemeColorCategory.SYNTAX,    new Color(0x4e, 0xc9, 0xb0));
        reg("syntax.method",          "Method Name",          ThemeColorCategory.SYNTAX,    new Color(0xdc, 0xdc, 0xaa));
        reg("syntax.field",           "Field Name",           ThemeColorCategory.SYNTAX,    new Color(0x9c, 0xdc, 0xfe));
        reg("syntax.param",           "Parameter",            ThemeColorCategory.SYNTAX,    new Color(0x9c, 0xdc, 0xfe));

        reg("code.primary",           "Primary Color (primColor)", ThemeColorCategory.EDITOR, new Color(0x56, 0x9c, 0xd6));
        reg("code.secondary",         "Secondary Color (secColor)", ThemeColorCategory.EDITOR, new Color(0xce, 0x91, 0x78));
        reg("code.opcode",            "Opcode",               ThemeColorCategory.EDITOR,    new Color(0x56, 0x9c, 0xd6));
        reg("code.descriptor",        "Descriptor",           ThemeColorCategory.EDITOR,    new Color(0x9b, 0x9b, 0x9b));

        reg("file.class",             "Class",                ThemeColorCategory.FILE_KIND, new Color(0x64, 0x95, 0xed));
        reg("file.interface",         "Interface",            ThemeColorCategory.FILE_KIND, new Color(0x63, 0xa2, 0x51));
        reg("file.abstract",          "Abstract Class",       ThemeColorCategory.FILE_KIND, new Color(0x77, 0x5a, 0xb0));
        reg("file.enum",              "Enum",                 ThemeColorCategory.FILE_KIND, new Color(0xc0, 0x85, 0x49));
        reg("file.resource",          "Resource",             ThemeColorCategory.FILE_KIND, new Color(0x9d, 0x39, 0x54));
        reg("file.annotation",        "Annotation",           ThemeColorCategory.FILE_KIND, new Color(0x88, 0x42, 0x91));

        reg("xref.inherit",           "Inherit",              ThemeColorCategory.XREF_KIND, new Color(0x60, 0x8c, 0x41));
        reg("xref.return",            "Return",               ThemeColorCategory.XREF_KIND, new Color(0x7b, 0x86, 0x49));
        reg("xref.parameter",         "Parameter",            ThemeColorCategory.XREF_KIND, new Color(0x86, 0x49, 0x5a));
        reg("xref.literal",           "Literal",              ThemeColorCategory.XREF_KIND, new Color(0x6f, 0xb4, 0xb2));
        reg("xref.exception",         "Exception",            ThemeColorCategory.XREF_KIND, new Color(0xc0, 0x7d, 0x46));
        reg("xref.annotation",        "Annotation",           ThemeColorCategory.XREF_KIND, new Color(0x88, 0x42, 0x91));
    }

    private static void reg(String key, String label, ThemeColorCategory cat, Color def) {
        ThemeToken token = new ThemeToken(key, label, cat, def);
        TOKENS.put(key, token);
        ORDERED.add(token);
    }

    private JBytePalette() {}

    public static ThemeToken get(String key) {
        return TOKENS.get(key);
    }

    public static Color color(String key) {
        ThemeToken t = TOKENS.get(key);
        return t != null ? t.getColor() : Color.GRAY;
    }

    public static String hex(String key) {
        ThemeToken t = TOKENS.get(key);
        return t != null ? t.toHex() : "#888888";
    }

    public static List<ThemeToken> all() {
        return Collections.unmodifiableList(ORDERED);
    }

    public static Map<String, ThemeToken> map() {
        return Collections.unmodifiableMap(TOKENS);
    }

    public static void apply(ThemeToken token, Color color) {
        token.setColor(color);
    }
}
