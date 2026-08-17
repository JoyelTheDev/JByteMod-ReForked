package dev.joyel.mapping.format;

import dev.joyel.mapping.MappingSet;

import java.util.Map;

public final class TinyV1MappingFormat implements MappingFormat {

    @Override
    public String getName() {
        return "Tiny v1";
    }

    @Override
    public String getFileExtension() {
        return "tiny";
    }

    @Override
    public MappingSet parse(String text) throws MappingParseException {
        MappingSet mappings = new MappingSet();
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0) return mappings;

        String header = lines[0];
        if (!header.startsWith("v1\t")) {
            throw new MappingParseException("Not a Tiny v1 file (header: " + header + ")");
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\t");
            if (parts.length < 1) continue;

            if (parts[0].equals("CLASS") && parts.length >= 3) {
                mappings.putClass(parts[1], parts[2]);

            } else if (parts[0].equals("FIELD") && parts.length >= 5) {
                mappings.putField(parts[1], parts[3], parts[2], parts[4]);

            } else if (parts[0].equals("METHOD") && parts.length >= 5) {
                mappings.putMethod(parts[1], parts[3], parts[2], parts[4]);
            }
        }
        return mappings;
    }

    @Override
    public String export(MappingSet mappings) {
        StringBuilder sb = new StringBuilder("v1\tofficial\tnamed\n");
        for (Map.Entry<String, String> e : mappings.getClassMappings().entrySet()) {
            sb.append("CLASS\t").append(e.getKey()).append("\t").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getFieldMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("FIELD\t").append(k.getOwner()).append("\t").append(k.getDesc())
              .append("\t").append(k.getName()).append("\t").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getMethodMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("METHOD\t").append(k.getOwner()).append("\t").append(k.getDesc())
              .append("\t").append(k.getName()).append("\t").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }
}
