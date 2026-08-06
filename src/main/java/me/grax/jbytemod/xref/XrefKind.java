package me.grax.jbytemod.xref;

import java.awt.Color;

public enum XrefKind {
    TYPE("Type", new Color(0x61AFEF)),
    INVOKE("Invoke", new Color(0x98C379)),
    FIELD("Field", new Color(0xE06C75)),
    INHERIT("Inherit", new Color(0xC678DD)),
    RETURN("Return", new Color(0xD19A66)),
    PARAMETER("Parameter", new Color(0x56B6C2)),
    ANNOTATION("Annotation", new Color(0xABB2BF)),
    EXCEPTION("Exception", new Color(0xE5C07B)),
    LITERAL("Literal", new Color(0x528BFF));

    private final String displayName;
    private final Color color;

    XrefKind(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getColor() {
        return color;
    }
}
