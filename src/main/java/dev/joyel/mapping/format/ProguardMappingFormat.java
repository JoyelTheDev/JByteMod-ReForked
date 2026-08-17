package dev.joyel.mapping.format;

import dev.joyel.mapping.MappingSet;

import java.util.Map;

public final class ProguardMappingFormat implements MappingFormat {

    @Override
    public String getName() {
        return "ProGuard";
    }

    @Override
    public String getFileExtension() {
        return "txt";
    }

    @Override
    public MappingSet parse(String text) throws MappingParseException {
        MappingSet mappings = new MappingSet();
        String currentOldClass = null;
        String currentNewClass = null;

        for (String line : text.split("\\r?\\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (!line.startsWith("    ")) {
                int arrow = line.indexOf(" -> ");
                if (arrow < 0) continue;
                String oldDot = line.substring(0, arrow).trim();
                String newDot = line.substring(arrow + 4).trim();
                if (newDot.endsWith(":")) newDot = newDot.substring(0, newDot.length() - 1);
                currentOldClass = dotToSlash(oldDot);
                currentNewClass = dotToSlash(newDot);
                mappings.putClass(currentOldClass, currentNewClass);
            } else {
                if (currentOldClass == null) continue;
                String trimmed = line.trim();
                int arrow = trimmed.indexOf(" -> ");
                if (arrow < 0) continue;
                String memberOld = trimmed.substring(0, arrow).trim();
                String memberNew = trimmed.substring(arrow + 4).trim();

                if (memberOld.contains("(")) {
                    int parenOpen = memberOld.indexOf('(');
                    int parenClose = memberOld.indexOf(')');
                    int colonStart = memberOld.lastIndexOf(':', parenOpen);
                    String retAndName;
                    if (colonStart >= 0) {
                        int colonEnd = memberOld.indexOf(':', colonStart + 1);
                        retAndName = colonEnd >= 0
                                ? memberOld.substring(colonEnd + 1, parenOpen).trim()
                                : memberOld.substring(colonStart + 1, parenOpen).trim();
                    } else {
                        retAndName = memberOld.substring(0, parenOpen).trim();
                    }
                    int lastSpace = retAndName.lastIndexOf(' ');
                    String retType = retAndName.substring(0, lastSpace).trim();
                    String methodName = retAndName.substring(lastSpace + 1).trim();
                    String params = memberOld.substring(parenOpen + 1, parenClose);
                    String desc = buildMethodDesc(params, retType);
                    mappings.putMethod(currentOldClass, methodName, desc, memberNew);
                } else {
                    int lastSpace = memberOld.lastIndexOf(' ');
                    if (lastSpace < 0) continue;
                    String fieldType = memberOld.substring(0, lastSpace).trim();
                    String fieldName = memberOld.substring(lastSpace + 1).trim();
                    String desc = typeToDesc(fieldType);
                    mappings.putField(currentOldClass, fieldName, desc, memberNew);
                }
            }
        }
        return mappings;
    }

    @Override
    public String export(MappingSet mappings) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : mappings.getClassMappings().entrySet()) {
            String oldDot = slashToDot(e.getKey());
            String newDot = slashToDot(e.getValue());
            sb.append(oldDot).append(" -> ").append(newDot).append(":\n");
        }
        return sb.toString();
    }

    private static String dotToSlash(String s) {
        return s.replace('.', '/');
    }

    private static String slashToDot(String s) {
        return s.replace('/', '.');
    }

    private static String buildMethodDesc(String params, String retType) {
        StringBuilder sb = new StringBuilder("(");
        if (!params.trim().isEmpty()) {
            for (String p : params.split(",")) {
                sb.append(typeToDesc(p.trim()));
            }
        }
        sb.append(")").append(typeToDesc(retType));
        return sb.toString();
    }

    private static String typeToDesc(String type) {
        if (type.endsWith("[]")) {
            return "[" + typeToDesc(type.substring(0, type.length() - 2));
        }
        switch (type) {
            case "void":    return "V";
            case "boolean": return "Z";
            case "byte":    return "B";
            case "char":    return "C";
            case "short":   return "S";
            case "int":     return "I";
            case "long":    return "J";
            case "float":   return "F";
            case "double":  return "D";
            default:        return "L" + type.replace('.', '/') + ";";
        }
    }
}
