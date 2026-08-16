package dev.joyel.search;

import de.xbrowniecodez.jbytemod.Main;
import de.xbrowniecodez.jbytemod.JByteMod;
import me.grax.jbytemod.ui.lists.entries.SearchEntry;
import me.grax.jbytemod.utils.InstrUtils;
import me.grax.jbytemod.utils.TextUtils;
import me.grax.jbytemod.utils.list.LazyListModel;
import me.lpk.util.OpUtils;
import org.objectweb.asm.tree.*;

import javax.swing.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GlobalSearchTask extends SwingWorker<List<SearchEntry>, Integer> {

    public enum SearchScope {
        LDC_STRINGS,
        LDC_NUMBERS,
        CLASS_NAMES,
        METHOD_NAMES,
        FIELD_NAMES,
        OPCODES
    }

    private final JByteMod jbm;
    private final String query;
    private final boolean regex;
    private final boolean caseSensitive;
    private final Set<SearchScope> scopes;
    private final GlobalSearchPanel panel;

    private Pattern compiledPattern;
    private String normalizedQuery;

    public GlobalSearchTask(JByteMod jbm, String query, boolean regex, boolean caseSensitive,
                            Set<SearchScope> scopes, GlobalSearchPanel panel) {
        this.jbm = jbm;
        this.query = query;
        this.regex = regex;
        this.caseSensitive = caseSensitive;
        this.scopes = scopes;
        this.panel = panel;
    }

    @Override
    protected List<SearchEntry> doInBackground() {
        List<SearchEntry> results = new ArrayList<>();

        if (regex) {
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                compiledPattern = Pattern.compile(query, flags);
            } catch (PatternSyntaxException e) {
                return results;
            }
        } else {
            normalizedQuery = caseSensitive ? query : query.toLowerCase(Locale.ROOT);
        }

        Collection<ClassNode> classes = jbm.getJarArchive().getClasses().values();
        double total = classes.size();
        int processed = 0;

        for (ClassNode cn : classes) {
            if (isCancelled()) break;

            if (scopes.contains(SearchScope.CLASS_NAMES)) {
                searchClassName(cn, results);
            }

            for (MethodNode mn : cn.methods) {
                if (scopes.contains(SearchScope.METHOD_NAMES)) {
                    searchMethodName(cn, mn, results);
                }
                for (AbstractInsnNode ain : mn.instructions) {
                    if (scopes.contains(SearchScope.LDC_STRINGS) || scopes.contains(SearchScope.LDC_NUMBERS)) {
                        searchLdc(cn, mn, ain, results);
                    }
                    if (scopes.contains(SearchScope.OPCODES)) {
                        searchOpcode(cn, mn, ain, results);
                    }
                }
            }

            for (FieldNode fn : cn.fields) {
                if (scopes.contains(SearchScope.FIELD_NAMES)) {
                    searchFieldName(cn, fn, results);
                }
            }

            publish((int) (++processed / total * 100));
        }

        return results;
    }

    private boolean matches(String value) {
        if (regex) {
            return compiledPattern.matcher(value).find();
        }
        String candidate = caseSensitive ? value : value.toLowerCase(Locale.ROOT);
        return candidate.contains(normalizedQuery);
    }

    private void searchLdc(ClassNode cn, MethodNode mn, AbstractInsnNode ain, List<SearchEntry> results) {
        if (ain.getType() != AbstractInsnNode.LDC_INSN) return;
        LdcInsnNode ldc = (LdcInsnNode) ain;
        boolean isString = ldc.cst instanceof String;
        boolean isNumber = ldc.cst instanceof Number;

        if (isString && !scopes.contains(SearchScope.LDC_STRINGS)) return;
        if (isNumber && !scopes.contains(SearchScope.LDC_NUMBERS)) return;
        if (!isString && !isNumber) return;

        String raw = ldc.cst.toString();
        if (matches(raw)) {
            String display = TextUtils.escape(TextUtils.max(raw, 120));
            String typeTag = isString
                    ? TextUtils.addTag("\"" + display + "\"", "font color=#559955")
                    : TextUtils.addTag(display, "font color=#5588cc");
            SearchEntry entry = new SearchEntry(cn, mn, display);
            entry.setText(TextUtils.toHtml(
                    InstrUtils.getDisplayClass(cn.name) + "."
                            + TextUtils.escape(mn.name) + " - " + typeTag));
            results.add(entry);
        }
    }

    private void searchClassName(ClassNode cn, List<SearchEntry> results) {
        if (!matches(cn.name)) return;
        if (cn.methods.isEmpty()) return;
        MethodNode first = cn.methods.get(0);
        String display = TextUtils.escape(TextUtils.max(cn.name, 120));
        SearchEntry entry = new SearchEntry(cn, first, display);
        entry.setText(TextUtils.toHtml(
                InstrUtils.getDisplayClass(cn.name) + " - "
                        + TextUtils.addTag(display, "font color=#cc8844")));
        results.add(entry);
    }

    private void searchMethodName(ClassNode cn, MethodNode mn, List<SearchEntry> results) {
        String combined = cn.name + "." + mn.name + mn.desc;
        if (!matches(combined)) return;
        String display = TextUtils.escape(TextUtils.max(mn.name + mn.desc, 120));
        SearchEntry entry = new SearchEntry(cn, mn, mn.name + mn.desc);
        entry.setText(TextUtils.toHtml(
                InstrUtils.getDisplayClass(cn.name) + "."
                        + TextUtils.addTag(TextUtils.escape(mn.name), "font color=#aa55cc")
                        + TextUtils.addTag(TextUtils.escape(mn.desc), "font color=#888888")));
        results.add(entry);
    }

    private void searchFieldName(ClassNode cn, FieldNode fn, List<SearchEntry> results) {
        String combined = cn.name + "." + fn.name + " " + fn.desc;
        if (!matches(combined)) return;
        if (cn.methods.isEmpty()) return;
        MethodNode stub = cn.methods.get(0);
        SearchEntry entry = new SearchEntry(cn, stub, cn.name + "." + fn.name + " " + fn.desc);
        entry.setText(TextUtils.toHtml(
                InstrUtils.getDisplayClass(cn.name) + "."
                        + TextUtils.addTag(TextUtils.escape(fn.name), "font color=#dd7755")
                        + " "
                        + TextUtils.addTag(TextUtils.escape(fn.desc), "font color=#888888")));
        results.add(entry);
    }

    private void searchOpcode(ClassNode cn, MethodNode mn, AbstractInsnNode ain, List<SearchEntry> results) {
        int op = ain.getOpcode();
        if (op < 0) return;
        String opName = OpUtils.getOpcodeText(op);
        if (opName == null) return;
        if (!matches(opName)) return;
        String display = TextUtils.escape(InstrUtils.toString(ain));
        SearchEntry entry = new SearchEntry(cn, mn, display);
        entry.setText(TextUtils.toHtml(
                InstrUtils.getDisplayClass(cn.name) + "."
                        + TextUtils.escape(mn.name) + " - "
                        + TextUtils.addTag(TextUtils.escape(opName.toLowerCase(Locale.ROOT)), "b")));
        results.add(entry);
    }

    @Override
    protected void process(List<Integer> chunks) {
        int progress = chunks.get(chunks.size() - 1);
        jbm.getPageEndPanel().setValue(progress);
    }

    @Override
    protected void done() {
        jbm.getPageEndPanel().setValue(100);
        try {
            List<SearchEntry> results = get();
            LazyListModel<SearchEntry> model = new LazyListModel<>();
            for (SearchEntry e : results) {
                model.addElement(e);
            }
            panel.setResults(model, results.size());
            Main.INSTANCE.getLogger().log("Global search finished: " + results.size() + " result(s).");
        } catch (Exception e) {
            Main.INSTANCE.getLogger().err("Global search failed: " + e.getMessage());
        }
    }
}