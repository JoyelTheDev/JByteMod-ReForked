package dev.joyel.tutorial;

public final class TutorialStep {
    public static final String HIGHLIGHT_NONE = "none";
    public static final String HIGHLIGHT_TREE = "tree";
    public static final String HIGHLIGHT_CODELIST = "codelist";
    public static final String HIGHLIGHT_MENUBAR = "menubar";
    public static final String HIGHLIGHT_TABS = "tabs";
    public static final String HIGHLIGHT_INFOBAR = "infobar";
    public static final String HIGHLIGHT_SEARCH = "search";
    public static final String HIGHLIGHT_TOOLBAR = "toolbar";

    private final String title;
    private final String body;
    private final String highlight;

    public TutorialStep(String title, String body, String highlight) {
        this.title = title;
        this.body = body;
        this.highlight = highlight;
    }

    public TutorialStep(String title, String body) {
        this(title, body, HIGHLIGHT_NONE);
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getHighlight() {
        return highlight;
    }
}