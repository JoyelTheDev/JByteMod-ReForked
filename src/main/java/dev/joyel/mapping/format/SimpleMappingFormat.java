package dev.joyel.mapping.format;

import dev.joyel.mapping.MappingSet;

import java.util.Map;

/**
 * Simple line-based format:
 *   c OldClass NewClass
 *   f OldClass OldField OldDesc NewField
 *   m OldClass OldMethod OldDesc NewMethod
 */
public final class SimpleMappingFormat implements MappingFormat {

    @Override
    public String getName() {
        return "Simple";
    }

    @Override
    public String getFileExtension() {
        return "map";
    }

    @Override
    public MappingSet parse(String text) throws MappingParseException {
        MappingSet mappings = new MappingSet();
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 1) continue;

            if (parts[0].equals("c") && parts.length >= 3) {
                mappings.putClass(parts[1], parts[2]);
            } else if (parts[0].equals("f") && parts.length >= 5) {
                mappings.putField(parts[1], parts[2], parts[3], parts[4]);
            } else if (parts[0].equals("m") && parts.length >= 5) {
                mappings.putMethod(parts[1], parts[2], parts[3], parts[4]);
            }
        }
        return mappings;
    }

    @Override
    public String export(MappingSet mappings) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : mappings.getClassMappings().entrySet()) {
            sb.append("c ").append(e.getKey()).append(" ").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getFieldMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("f ").append(k.getOwner()).append(" ").append(k.getName())
              .append(" ").append(k.getDesc()).append(" ").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getMethodMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("m ").append(k.getOwner()).append(" ").append(k.getName())
              .append(" ").append(k.getDesc()).append(" ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }
}
