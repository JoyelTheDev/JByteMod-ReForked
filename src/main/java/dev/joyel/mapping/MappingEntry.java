package dev.joyel.mapping;

public final class MappingEntry {
    private final String oldName;
    private final String newName;

    public MappingEntry(String oldName, String newName) {
        this.oldName = oldName;
        this.newName = newName;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewName() {
        return newName;
    }

    @Override
    public String toString() {
        return oldName + " -> " + newName;
    }
}
