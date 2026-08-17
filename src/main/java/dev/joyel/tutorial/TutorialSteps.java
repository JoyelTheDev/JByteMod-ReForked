package dev.joyel.tutorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TutorialSteps {

    private TutorialSteps() {}

    public static List<TutorialStep> buildSteps() {
        List<TutorialStep> steps = new ArrayList<TutorialStep>();

        steps.add(new TutorialStep(
            "Welcome to JByteMod ReForked",
            "<html><body style='width:340px;font-size:13px'>"
            + "<b>JByteMod ReForked</b> is a Java bytecode editor — it lets you open, "
            + "inspect, and modify compiled <code>.jar</code> and <code>.class</code> files "
            + "without needing source code.<br><br>"
            + "This guided tour walks you through the main areas of the tool in about "
            + "2 minutes. You can close it at any time and re-open it from "
            + "<b>Help &rsaquo; Tutorial</b>.<br><br>"
            + "Click <b>Next</b> to begin."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_NONE
        ));

        steps.add(new TutorialStep(
            "Opening a JAR or Class File",
            "<html><body style='width:340px;font-size:13px'>"
            + "Use <b>File &rsaquo; Load</b> (or <code>Ctrl+N</code>) to open a "
            + "<code>.jar</code>, <code>.class</code>, or <code>.apk</code> file.<br><br>"
            + "Once loaded, every class inside the archive appears in the "
            + "<b>Class Tree</b> on the left side of the window, organised by package.<br><br>"
            + "You can reload a changed file on disk at any time using "
            + "<b>File &rsaquo; Refresh</b>."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_MENUBAR
        ));

        steps.add(new TutorialStep(
            "The Class Tree",
            "<html><body style='width:340px;font-size:13px'>"
            + "The <b>Class Tree</b> lists all packages and classes in the loaded file.<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li>Click a <b>class</b> to open its fields in the bytecode list.</li>"
            + "<li>Expand a class node to see its <b>methods</b>.</li>"
            + "<li>Click a <b>method</b> to load its bytecode instructions.</li>"
            + "</ul><br>"
            + "Right-click any node for context options such as adding or deleting "
            + "classes and methods."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_TREE
        ));

        steps.add(new TutorialStep(
            "The Bytecode Instruction List",
            "<html><body style='width:340px;font-size:13px'>"
            + "After selecting a method, all its JVM <b>bytecode instructions</b> appear "
            + "in the central list.<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>Double-click</b> an instruction to open its editor.</li>"
            + "<li><b>Right-click</b> for options: insert before/after, delete, copy.</li>"
            + "<li>Drag instructions to reorder them.</li>"
            + "</ul><br>"
            + "Changes are applied in-memory. Use <b>File &rsaquo; Save</b> to write "
            + "them back to disk."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_CODELIST
        ));

        steps.add(new TutorialStep(
            "The Tabs Panel",
            "<html><body style='width:340px;font-size:13px'>"
            + "The right side holds several tabs:<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>Bytecode</b> — the instruction list you just learned about.</li>"
            + "<li><b>Decompiler</b> — a decompiled Java source preview (read-only).</li>"
            + "<li><b>Control Flow</b> — a block-level control flow graph for the method.</li>"
            + "<li><b>TCB List</b> — try/catch block table for the method.</li>"
            + "<li><b>Local Variables</b> — local variable table for the method.</li>"
            + "<li><b>Script Console</b> — run Groovy or BeanShell scripts against the loaded JAR.</li>"
            + "<li><b>Global Search</b> — regex search across all class names, methods, and strings.</li>"
            + "<li><b>Metrics</b> — size and complexity stats for classes and methods.</li>"
            + "</ul>"
            + "</body></html>",
            TutorialStep.HIGHLIGHT_TABS
        ));

        steps.add(new TutorialStep(
            "The Info Panel",
            "<html><body style='width:340px;font-size:13px'>"
            + "The <b>Info Panel</b> at the bottom of the window shows details about "
            + "the currently selected class or method:<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li>Class name, superclass, and interfaces.</li>"
            + "<li>Access flags (public, final, abstract, etc.).</li>"
            + "<li>Method descriptor and exception table.</li>"
            + "<li>Stack size and local variable count.</li>"
            + "</ul><br>"
            + "You can edit most of these fields directly."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_INFOBAR
        ));

        steps.add(new TutorialStep(
            "Searching the JAR",
            "<html><body style='width:340px;font-size:13px'>"
            + "JByteMod offers several ways to search the loaded archive:<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>Search &rsaquo; Search LDC</b> — find string/number constants.</li>"
            + "<li><b>Search &rsaquo; Search Field / Method</b> — find usages of a member.</li>"
            + "<li><b>Search &rsaquo; Replace LDC</b> — find and replace constants in bulk.</li>"
            + "<li><b>Search &rsaquo; Global Search (Ctrl+Shift+F)</b> — regex search across everything.</li>"
            + "<li><b>Search &rsaquo; Find Main Class</b> — jump straight to the entry point.</li>"
            + "</ul>"
            + "</body></html>",
            TutorialStep.HIGHLIGHT_SEARCH
        ));

        steps.add(new TutorialStep(
            "Deobfuscation Tools",
            "<html><body style='width:340px;font-size:13px'>"
            + "Under <b>Utils &rsaquo; Deobf Tools</b> you'll find automated cleanup passes:<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>Peephole Optimizer</b> — combines several passes in one click.</li>"
            + "<li><b>Signature Fix</b> — removes illegal generic signatures.</li>"
            + "<li><b>Line Number Remove</b> — strips debug line number info.</li>"
            + "<li><b>Fold Constant</b> — simplifies constant arithmetic.</li>"
            + "<li><b>Rearrange Goto</b> — collapses unnecessary jump chains.</li>"
            + "<li><b>String Decryptor</b> — runs your own decryption method on all strings.</li>"
            + "</ul>"
            + "</body></html>",
            TutorialStep.HIGHLIGHT_MENUBAR
        ));

        steps.add(new TutorialStep(
            "Mapping / Renaming (New Feature)",
            "<html><body style='width:340px;font-size:13px'>"
            + "The <b>Mapping</b> menu gives you full renaming support:<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>Import</b> — load ProGuard, SRG, Tiny v1, Enigma, or Simple mappings.</li>"
            + "<li><b>Export</b> — save all applied renames back to any mapping format.</li>"
            + "<li><b>Auto-Rename</b> — automatically generate readable names for obfuscated "
            +     "classes, fields, and methods using incrementing or alphabet naming.</li>"
            + "<li><b>History</b> — view all applied mapping layers and undo the last one.</li>"
            + "</ul><br>"
            + "Open it with <b>Ctrl+Shift+M</b>."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_MENUBAR
        ));

        steps.add(new TutorialStep(
            "Advanced Tools",
            "<html><body style='width:340px;font-size:13px'>"
            + "A few more powerful tools worth knowing:<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>Pattern Matching</b> — find instruction sequences by opcode pattern.</li>"
            + "<li><b>Method Hierarchy</b> — see all overrides and implementations of a method.</li>"
            + "<li><b>Method Graph</b> — visualise the call structure inside a method.</li>"
            + "<li><b>Constant Pool</b> — inspect the raw constant pool of a class.</li>"
            + "<li><b>Hex Editor</b> — view and edit raw bytes of any class or resource.</li>"
            + "<li><b>JDWP Debugger</b> — attach to a running JVM and debug live.</li>"
            + "<li><b>Xreference</b> — find all usages of a class, field, or method.</li>"
            + "</ul>"
            + "</body></html>",
            TutorialStep.HIGHLIGHT_MENUBAR
        ));

        steps.add(new TutorialStep(
            "Undo / Redo",
            "<html><body style='width:340px;font-size:13px'>"
            + "Every instruction edit is tracked per-method.<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>Ctrl+Z</b> — undo the last change to the current method.</li>"
            + "<li><b>Ctrl+Y</b> — redo the last undone change.</li>"
            + "</ul><br>"
            + "The undo history is held in memory for the current session. "
            + "Switching to a different method and back preserves the history for each "
            + "method independently."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_CODELIST
        ));

        steps.add(new TutorialStep(
            "Saving Your Changes",
            "<html><body style='width:340px;font-size:13px'>"
            + "When you are done editing:<br><br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li><b>File &rsaquo; Save (Ctrl+S)</b> — overwrite the last opened/saved file.</li>"
            + "<li><b>File &rsaquo; Save As</b> — choose a new output path (recommended to "
            +     "avoid losing your original).</li>"
            + "</ul><br>"
            + "All in-memory changes across every modified class are written to the "
            + "output JAR in one step. The original file is never modified unless you "
            + "explicitly overwrite it."
            + "</body></html>",
            TutorialStep.HIGHLIGHT_TOOLBAR
        ));

        steps.add(new TutorialStep(
            "You're Ready!",
            "<html><body style='width:340px;font-size:13px'>"
            + "That covers the essentials of <b>JByteMod ReForked</b>.<br><br>"
            + "A few quick tips to remember:<br>"
            + "<ul style='margin:0;padding-left:18px'>"
            + "<li>Always save to a <i>new</i> file first when editing important JARs.</li>"
            + "<li>Use the Script Console for bulk operations across many classes.</li>"
            + "<li>The Decompiler tab helps you understand what code does before editing it.</li>"
            + "<li>Re-open this tutorial anytime from <b>Help &rsaquo; Tutorial</b>.</li>"
            + "</ul><br>"
            + "<b>Happy reverse engineering!</b>"
            + "</body></html>",
            TutorialStep.HIGHLIGHT_NONE
        ));

        return Collections.unmodifiableList(steps);
    }
}
