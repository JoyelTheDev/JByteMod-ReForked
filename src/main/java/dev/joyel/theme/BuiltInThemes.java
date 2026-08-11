package dev.joyel.theme;

import java.awt.Color;

public final class BuiltInThemes {
    private BuiltInThemes() {}

    public static JByteTheme dark() {
        JByteTheme t = new JByteTheme("Dark", true);
        t.setColor("editor.background",    new Color(0x2b, 0x2b, 0x2b));
        t.setColor("editor.foreground",    new Color(0xd4, 0xd4, 0xd4));
        t.setColor("editor.selection",     new Color(0x21, 0x42, 0x83));
        t.setColor("editor.linehighlight", new Color(0x32, 0x32, 0x32));
        t.setColor("editor.caret",         new Color(0xd4, 0xd4, 0xd4));
        t.setColor("editor.border",        new Color(0x44, 0x44, 0x44));

        t.setColor("syntax.keyword",  new Color(0x56, 0x9c, 0xd6));
        t.setColor("syntax.string",   new Color(0xce, 0x91, 0x78));
        t.setColor("syntax.number",   new Color(0xb5, 0xce, 0xa8));
        t.setColor("syntax.comment",  new Color(0x6a, 0x99, 0x55));
        t.setColor("syntax.label",    new Color(0x9b, 0x9b, 0x9b));
        t.setColor("syntax.type",     new Color(0x4e, 0xc9, 0xb0));
        t.setColor("syntax.method",   new Color(0xdc, 0xdc, 0xaa));
        t.setColor("syntax.field",    new Color(0x9c, 0xdc, 0xfe));
        t.setColor("syntax.param",    new Color(0x9c, 0xdc, 0xfe));

        t.setColor("code.primary",    new Color(0x56, 0x9c, 0xd6));
        t.setColor("code.secondary",  new Color(0xce, 0x91, 0x78));
        t.setColor("code.opcode",     new Color(0x56, 0x9c, 0xd6));
        t.setColor("code.descriptor", new Color(0x9b, 0x9b, 0x9b));

        t.setColor("file.class",      new Color(0x64, 0x95, 0xed));
        t.setColor("file.interface",  new Color(0x63, 0xa2, 0x51));
        t.setColor("file.abstract",   new Color(0x77, 0x5a, 0xb0));
        t.setColor("file.enum",       new Color(0xc0, 0x85, 0x49));
        t.setColor("file.resource",   new Color(0x9d, 0x39, 0x54));
        t.setColor("file.annotation", new Color(0x88, 0x42, 0x91));

        t.setColor("xref.inherit",    new Color(0x60, 0x8c, 0x41));
        t.setColor("xref.return",     new Color(0x7b, 0x86, 0x49));
        t.setColor("xref.parameter",  new Color(0x86, 0x49, 0x5a));
        t.setColor("xref.literal",    new Color(0x6f, 0xb4, 0xb2));
        t.setColor("xref.exception",  new Color(0xc0, 0x7d, 0x46));
        t.setColor("xref.annotation", new Color(0x88, 0x42, 0x91));
        return t;
    }

    public static JByteTheme light() {
        JByteTheme t = new JByteTheme("Light", true);
        t.setColor("editor.background",    new Color(0xff, 0xff, 0xff));
        t.setColor("editor.foreground",    new Color(0x1e, 0x1e, 0x1e));
        t.setColor("editor.selection",     new Color(0xad, 0xd6, 0xff));
        t.setColor("editor.linehighlight", new Color(0xf0, 0xf0, 0xf0));
        t.setColor("editor.caret",         new Color(0x1e, 0x1e, 0x1e));
        t.setColor("editor.border",        new Color(0xcc, 0xcc, 0xcc));

        t.setColor("syntax.keyword",  new Color(0x00, 0x00, 0xff));
        t.setColor("syntax.string",   new Color(0xa3, 0x15, 0x15));
        t.setColor("syntax.number",   new Color(0x09, 0x86, 0x58));
        t.setColor("syntax.comment",  new Color(0x00, 0x80, 0x00));
        t.setColor("syntax.label",    new Color(0x79, 0x79, 0x79));
        t.setColor("syntax.type",     new Color(0x26, 0x7f, 0x99));
        t.setColor("syntax.method",   new Color(0x79, 0x5e, 0x26));
        t.setColor("syntax.field",    new Color(0x00, 0x10, 0xc0));
        t.setColor("syntax.param",    new Color(0x00, 0x10, 0xc0));

        t.setColor("code.primary",    new Color(0x00, 0x00, 0xff));
        t.setColor("code.secondary",  new Color(0xa3, 0x15, 0x15));
        t.setColor("code.opcode",     new Color(0x00, 0x00, 0xff));
        t.setColor("code.descriptor", new Color(0x79, 0x79, 0x79));

        t.setColor("file.class",      new Color(0x26, 0x7f, 0x99));
        t.setColor("file.interface",  new Color(0x09, 0x86, 0x58));
        t.setColor("file.abstract",   new Color(0x6f, 0x42, 0xc1));
        t.setColor("file.enum",       new Color(0xa3, 0x15, 0x15));
        t.setColor("file.resource",   new Color(0xc0, 0x00, 0x00));
        t.setColor("file.annotation", new Color(0x79, 0x5e, 0x26));

        t.setColor("xref.inherit",    new Color(0x09, 0x86, 0x58));
        t.setColor("xref.return",     new Color(0x79, 0x5e, 0x26));
        t.setColor("xref.parameter",  new Color(0x6f, 0x42, 0xc1));
        t.setColor("xref.literal",    new Color(0x26, 0x7f, 0x99));
        t.setColor("xref.exception",  new Color(0xc0, 0x00, 0x00));
        t.setColor("xref.annotation", new Color(0x79, 0x5e, 0x26));
        return t;
    }

    public static JByteTheme monokai() {
        JByteTheme t = new JByteTheme("Monokai", true);
        t.setColor("editor.background",    new Color(0x27, 0x28, 0x22));
        t.setColor("editor.foreground",    new Color(0xf8, 0xf8, 0xf2));
        t.setColor("editor.selection",     new Color(0x49, 0x48, 0x3e));
        t.setColor("editor.linehighlight", new Color(0x3e, 0x3d, 0x32));
        t.setColor("editor.caret",         new Color(0xf8, 0xf8, 0xf0));
        t.setColor("editor.border",        new Color(0x44, 0x44, 0x44));

        t.setColor("syntax.keyword",  new Color(0xf9, 0x26, 0x72));
        t.setColor("syntax.string",   new Color(0xe6, 0xdb, 0x74));
        t.setColor("syntax.number",   new Color(0xae, 0x81, 0xff));
        t.setColor("syntax.comment",  new Color(0x75, 0x71, 0x5e));
        t.setColor("syntax.label",    new Color(0x75, 0x71, 0x5e));
        t.setColor("syntax.type",     new Color(0x66, 0xd9, 0xe8));
        t.setColor("syntax.method",   new Color(0xa6, 0xe2, 0x2e));
        t.setColor("syntax.field",    new Color(0x66, 0xd9, 0xe8));
        t.setColor("syntax.param",    new Color(0xfd, 0x97, 0x1f));

        t.setColor("code.primary",    new Color(0x66, 0xd9, 0xe8));
        t.setColor("code.secondary",  new Color(0xf9, 0x26, 0x72));
        t.setColor("code.opcode",     new Color(0xf9, 0x26, 0x72));
        t.setColor("code.descriptor", new Color(0x75, 0x71, 0x5e));

        t.setColor("file.class",      new Color(0x66, 0xd9, 0xe8));
        t.setColor("file.interface",  new Color(0xa6, 0xe2, 0x2e));
        t.setColor("file.abstract",   new Color(0xae, 0x81, 0xff));
        t.setColor("file.enum",       new Color(0xfd, 0x97, 0x1f));
        t.setColor("file.resource",   new Color(0xf9, 0x26, 0x72));
        t.setColor("file.annotation", new Color(0xe6, 0xdb, 0x74));

        t.setColor("xref.inherit",    new Color(0xa6, 0xe2, 0x2e));
        t.setColor("xref.return",     new Color(0xe6, 0xdb, 0x74));
        t.setColor("xref.parameter",  new Color(0xae, 0x81, 0xff));
        t.setColor("xref.literal",    new Color(0x66, 0xd9, 0xe8));
        t.setColor("xref.exception",  new Color(0xf9, 0x26, 0x72));
        t.setColor("xref.annotation", new Color(0xe6, 0xdb, 0x74));
        return t;
    }

    public static JByteTheme solarizedDark() {
        JByteTheme t = new JByteTheme("Solarized Dark", true);
        t.setColor("editor.background",    new Color(0x00, 0x2b, 0x36));
        t.setColor("editor.foreground",    new Color(0x83, 0x94, 0x96));
        t.setColor("editor.selection",     new Color(0x07, 0x36, 0x42));
        t.setColor("editor.linehighlight", new Color(0x07, 0x36, 0x42));
        t.setColor("editor.caret",         new Color(0x83, 0x94, 0x96));
        t.setColor("editor.border",        new Color(0x07, 0x36, 0x42));

        t.setColor("syntax.keyword",  new Color(0x85, 0x99, 0x00));
        t.setColor("syntax.string",   new Color(0x2a, 0xa1, 0x98));
        t.setColor("syntax.number",   new Color(0xd3, 0x36, 0x82));
        t.setColor("syntax.comment",  new Color(0x58, 0x6e, 0x75));
        t.setColor("syntax.label",    new Color(0x58, 0x6e, 0x75));
        t.setColor("syntax.type",     new Color(0x26, 0x8b, 0xd2));
        t.setColor("syntax.method",   new Color(0x6c, 0x71, 0xc4));
        t.setColor("syntax.field",    new Color(0x26, 0x8b, 0xd2));
        t.setColor("syntax.param",    new Color(0xcb, 0x4b, 0x16));

        t.setColor("code.primary",    new Color(0x26, 0x8b, 0xd2));
        t.setColor("code.secondary",  new Color(0xcb, 0x4b, 0x16));
        t.setColor("code.opcode",     new Color(0x85, 0x99, 0x00));
        t.setColor("code.descriptor", new Color(0x58, 0x6e, 0x75));

        t.setColor("file.class",      new Color(0x26, 0x8b, 0xd2));
        t.setColor("file.interface",  new Color(0x85, 0x99, 0x00));
        t.setColor("file.abstract",   new Color(0x6c, 0x71, 0xc4));
        t.setColor("file.enum",       new Color(0xcb, 0x4b, 0x16));
        t.setColor("file.resource",   new Color(0xd3, 0x36, 0x82));
        t.setColor("file.annotation", new Color(0xb5, 0x89, 0x00));

        t.setColor("xref.inherit",    new Color(0x85, 0x99, 0x00));
        t.setColor("xref.return",     new Color(0x2a, 0xa1, 0x98));
        t.setColor("xref.parameter",  new Color(0x6c, 0x71, 0xc4));
        t.setColor("xref.literal",    new Color(0x26, 0x8b, 0xd2));
        t.setColor("xref.exception",  new Color(0xd3, 0x36, 0x82));
        t.setColor("xref.annotation", new Color(0xb5, 0x89, 0x00));
        return t;
    }
}
