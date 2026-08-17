package dev.joyel.mapping.format;

import dev.joyel.mapping.MappingSet;

import java.util.Map;

public final class SrgMappingFormat implements MappingFormat {

    @Override
    public String getName() {
        return "SRG";
    }

    @Override
    public String getFileExtension() {
        return "srg";
    }

    @Override
    public MappingSet parse(String text) throws MappingParseException {
        MappingSet mappings = new MappingSet();
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.startsWith("CL: ")) {
                String[] parts = line.substring(4).trim().split(" ");
                if (parts.length >= 2) mappings.putClass(parts[0], parts[1]);

            } else if (line.startsWith("FD: ")) {
                String[] parts = line.substring(4).trim().split(" ");
                if (parts.length >= 2) {
                    String[] oldSplit = splitOwnerMember(parts[0]);
                    String[] newSplit = splitOwnerMember(parts[1]);
                    if (oldSplit != null && newSplit != null) {
                        mappings.putField(oldSplit[0], oldSplit[1], "", newSplit[1]);
                    }
                }

            } else if (line.startsWith("MD: ")) {
                String[] parts = line.substring(4).trim().split(" ");
                if (parts.length >= 4) {
                    String[] oldSplit = splitOwnerMember(parts[0]);
                    String oldDesc = parts[1];
                    String[] newSplit = splitOwnerMember(parts[2]);
                    if (oldSplit != null && newSplit != null) {
                        mappings.putMethod(oldSplit[0], oldSplit[1], oldDesc, newSplit[1]);
                    }
                }
            }
        }
        return mappings;
    }

    @Override
    public String export(MappingSet mappings) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : mappings.getClassMappings().entrySet()) {
            sb.append("CL: ").append(e.getKey()).append(" ").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getFieldMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("FD: ").append(k.getOwner()).append("/").append(k.getName())
              .append(" ").append(k.getOwner()).append("/").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getMethodMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("MD: ").append(k.getOwner()).append("/").append(k.getName())
              .append(" ").append(k.getDesc())
              .append(" ").append(k.getOwner()).append("/").append(e.getValue())
              .append(" ").append(k.getDesc()).append("\n");
        }
        return sb.toString();
    }

    private static String[] splitOwnerMember(String ownerSlashMember) {
        int slash = ownerSlashMember.lastIndexOf('/');
        if (slash < 0) return null;
        return new String[]{ownerSlashMember.substring(0, slash), ownerSlashMember.substring(slash + 1)};
    }
}
