package de.xbrowniecodez.jbytemod.diff;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiffResult implements Serializable {

    private final List<BytecodeDiff> diffs = new ArrayList<>();
    private String originalJar;
    private String modifiedJar;
    private long timestamp;

    public DiffResult(String originalJar, String modifiedJar) {
        this.originalJar = originalJar;
        this.modifiedJar = modifiedJar;
        this.timestamp = System.currentTimeMillis();
    }

    public void addDiff(BytecodeDiff diff) {
        diffs.add(diff);
    }

    public void addAll(List<BytecodeDiff> diffList) {
        diffs.addAll(diffList);
    }

    public List<BytecodeDiff> getDiffs() {
        return Collections.unmodifiableList(diffs);
    }

    public List<BytecodeDiff> getDiffsForClass(String className) {
        List<BytecodeDiff> result = new ArrayList<>();
        for (BytecodeDiff d : diffs) {
            if (d.getClassName().equals(className)) {
                result.add(d);
            }
        }
        return result;
    }

    public List<BytecodeDiff> getDiffsOfType(BytecodeDiff.DiffType type) {
        List<BytecodeDiff> result = new ArrayList<>();
        for (BytecodeDiff d : diffs) {
            if (d.getType() == type) {
                result.add(d);
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return diffs.isEmpty();
    }

    public int size() {
        return diffs.size();
    }

    public String getOriginalJar() { return originalJar; }
    public String getModifiedJar() { return modifiedJar; }
    public long getTimestamp() { return timestamp; }

    public String getSummary() {
        int added = 0, removed = 0, modified = 0, methAdded = 0, methRemoved = 0, fieldAdded = 0, fieldRemoved = 0, accessChanged = 0;
        for (BytecodeDiff d : diffs) {
            switch (d.getType()) {
                case ADDED: added++; break;
                case REMOVED: removed++; break;
                case MODIFIED: modified++; break;
                case METHOD_ADDED: methAdded++; break;
                case METHOD_REMOVED: methRemoved++; break;
                case FIELD_ADDED: fieldAdded++; break;
                case FIELD_REMOVED: fieldRemoved++; break;
                case CLASS_ACCESS_CHANGED: accessChanged++; break;
            }
        }
        return String.format(
            "Total: %d changes | Insn: +%d -%d ~%d | Methods: +%d -%d | Fields: +%d -%d | Access: %d",
            diffs.size(), added, removed, modified, methAdded, methRemoved, fieldAdded, fieldRemoved, accessChanged
        );
    }
}
