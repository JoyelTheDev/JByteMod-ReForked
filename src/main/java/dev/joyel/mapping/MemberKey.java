package dev.joyel.mapping;

public final class MemberKey {
    private final String owner;
    private final String name;
    private final String desc;

    public MemberKey(String owner, String name, String desc) {
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemberKey)) return false;
        MemberKey that = (MemberKey) o;
        return owner.equals(that.owner) && name.equals(that.name) && desc.equals(that.desc);
    }

    @Override
    public int hashCode() {
        int result = owner.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + desc.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return owner + "." + name + desc;
    }
}
