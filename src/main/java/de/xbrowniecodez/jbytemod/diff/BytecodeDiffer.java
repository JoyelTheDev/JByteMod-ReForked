package de.xbrowniecodez.jbytemod.diff;

import org.objectweb.asm.tree.*;

import java.util.*;

public class BytecodeDiffer {

    public DiffResult diff(Map<String, ClassNode> original, Map<String, ClassNode> modified,
                           String originalName, String modifiedName) {
        DiffResult result = new DiffResult(originalName, modifiedName);

        Set<String> allClasses = new LinkedHashSet<>();
        allClasses.addAll(original.keySet());
        allClasses.addAll(modified.keySet());

        for (String className : allClasses) {
            ClassNode origClass = original.get(className);
            ClassNode modClass = modified.get(className);
            result.addAll(diffClass(origClass, modClass, className));
        }

        return result;
    }

    private List<BytecodeDiff> diffClass(ClassNode orig, ClassNode mod, String className) {
        List<BytecodeDiff> diffs = new ArrayList<>();

        if (orig == null) {
            diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.METHOD_ADDED, className, "<class>", "", -1, null, "class added"));
            return diffs;
        }
        if (mod == null) {
            diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.METHOD_REMOVED, className, "<class>", "", -1, "class existed", null));
            return diffs;
        }

        if (orig.access != mod.access) {
            diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.CLASS_ACCESS_CHANGED, className, null, null, -1,
                    String.valueOf(orig.access), String.valueOf(mod.access)));
        }

        diffs.addAll(diffFields(orig, mod, className));
        diffs.addAll(diffMethods(orig, mod, className));

        return diffs;
    }

    private List<BytecodeDiff> diffFields(ClassNode orig, ClassNode mod, String className) {
        List<BytecodeDiff> diffs = new ArrayList<>();

        Map<String, FieldNode> origFields = indexFields(orig.fields);
        Map<String, FieldNode> modFields = indexFields(mod.fields);

        for (String key : origFields.keySet()) {
            if (!modFields.containsKey(key)) {
                FieldNode fn = origFields.get(key);
                diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.FIELD_REMOVED, className, fn.name, fn.desc, -1, key, null));
            }
        }
        for (String key : modFields.keySet()) {
            if (!origFields.containsKey(key)) {
                FieldNode fn = modFields.get(key);
                diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.FIELD_ADDED, className, fn.name, fn.desc, -1, null, key));
            }
        }

        return diffs;
    }

    private Map<String, FieldNode> indexFields(List<FieldNode> fields) {
        Map<String, FieldNode> map = new LinkedHashMap<>();
        if (fields == null) return map;
        for (FieldNode fn : fields) {
            map.put(fn.name + ":" + fn.desc, fn);
        }
        return map;
    }

    private List<BytecodeDiff> diffMethods(ClassNode orig, ClassNode mod, String className) {
        List<BytecodeDiff> diffs = new ArrayList<>();

        Map<String, MethodNode> origMethods = indexMethods(orig.methods);
        Map<String, MethodNode> modMethods = indexMethods(mod.methods);

        for (String key : origMethods.keySet()) {
            if (!modMethods.containsKey(key)) {
                MethodNode mn = origMethods.get(key);
                diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.METHOD_REMOVED, className, mn.name, mn.desc, -1, key, null));
            }
        }
        for (String key : modMethods.keySet()) {
            if (!origMethods.containsKey(key)) {
                MethodNode mn = modMethods.get(key);
                diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.METHOD_ADDED, className, mn.name, mn.desc, -1, null, key));
            }
        }
        for (String key : origMethods.keySet()) {
            if (modMethods.containsKey(key)) {
                MethodNode origMn = origMethods.get(key);
                MethodNode modMn = modMethods.get(key);
                diffs.addAll(diffInstructions(origMn, modMn, className));
            }
        }

        return diffs;
    }

    private Map<String, MethodNode> indexMethods(List<MethodNode> methods) {
        Map<String, MethodNode> map = new LinkedHashMap<>();
        if (methods == null) return map;
        for (MethodNode mn : methods) {
            map.put(mn.name + mn.desc, mn);
        }
        return map;
    }

    private List<BytecodeDiff> diffInstructions(MethodNode orig, MethodNode mod, String className) {
        List<BytecodeDiff> diffs = new ArrayList<>();

        List<AbstractInsnNode> origInsns = getRealInsns(orig);
        List<AbstractInsnNode> modInsns = getRealInsns(mod);

        int origSize = origInsns.size();
        int modSize = modInsns.size();

        int[][] lcs = computeLCS(origInsns, modInsns);

        List<int[]> editScript = buildEditScript(lcs, origInsns, modInsns, origSize, modSize);

        int realIndex = 0;
        int origIdx = 0;
        int modIdx = 0;

        for (int[] op : editScript) {
            int opType = op[0];
            int oIdx = op[1];
            int mIdx = op[2];

            if (opType == 0) {
                origIdx++;
                modIdx++;
                realIndex++;
            } else if (opType == -1) {
                String oldVal = InsnSerializer.serialize(origInsns.get(oIdx));
                diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.REMOVED, className, orig.name, orig.desc, oIdx, oldVal, null));
                origIdx++;
            } else if (opType == 1) {
                String newVal = InsnSerializer.serialize(modInsns.get(mIdx));
                diffs.add(new BytecodeDiff(BytecodeDiff.DiffType.ADDED, className, orig.name, orig.desc, mIdx, null, newVal));
                modIdx++;
            }
        }

        return diffs;
    }

    private List<AbstractInsnNode> getRealInsns(MethodNode mn) {
        List<AbstractInsnNode> list = new ArrayList<>();
        if (mn.instructions == null) return list;
        for (AbstractInsnNode insn : mn.instructions) {
            if (insn.getOpcode() != -1) {
                list.add(insn);
            }
        }
        return list;
    }

    private int[][] computeLCS(List<AbstractInsnNode> a, List<AbstractInsnNode> b) {
        int m = a.size();
        int n = b.size();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                String sa = InsnSerializer.serialize(a.get(i - 1));
                String sb = InsnSerializer.serialize(b.get(j - 1));
                if (sa.equals(sb)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp;
    }

    private List<int[]> buildEditScript(int[][] lcs, List<AbstractInsnNode> a, List<AbstractInsnNode> b, int m, int n) {
        List<int[]> script = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 &&
                    InsnSerializer.serialize(a.get(i - 1)).equals(InsnSerializer.serialize(b.get(j - 1)))) {
                script.add(new int[]{0, i - 1, j - 1});
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                script.add(new int[]{1, i, j - 1});
                j--;
            } else {
                script.add(new int[]{-1, i - 1, j});
                i--;
            }
        }
        Collections.reverse(script);
        return script;
    }
}
