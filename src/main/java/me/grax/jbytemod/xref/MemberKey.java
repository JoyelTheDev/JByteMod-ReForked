package me.grax.jbytemod.xref;

import java.util.Objects;

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
        MemberKey other = (MemberKey) o;
        return Objects.equals(owner, other.owner)
                && Objects.equals(name, other.name)
                && Objects.equals(desc, other.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, desc);
    }

    @Override
    public String toString() {
        return owner + "." + name + desc;
    }
}
