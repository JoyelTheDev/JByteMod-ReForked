package dev.joyel.hex;

public final class ResourceEntry {
    private final String path;
    private byte[] data;

    public ResourceEntry(String path, byte[] data) {
        this.path = path;
        this.data = data != null ? data : new byte[0];
    }

    public String getPath() { return path; }

    public String getDisplayName() {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public byte[] getData() { return data; }

    public void setData(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

    public int size() { return data.length; }

    @Override
    public String toString() { return getDisplayName(); }
}
