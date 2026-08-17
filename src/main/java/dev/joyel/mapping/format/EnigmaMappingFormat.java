package dev.joyel.mapping.format;

import dev.joyel.mapping.MappingSet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

public final class EnigmaMappingFormat implements MappingFormat {

    @Override
    public String getName() {
        return "Enigma";
    }

    @Override
    public String getFileExtension() {
        return "mapping";
    }

    @Override
    public MappingSet parse(String text) throws MappingParseException {
        MappingSet mappings = new MappingSet();
        Deque<String> classStack = new ArrayDeque<String>();
        Deque<String> mappedClassStack = new ArrayDeque<String>();

        for (String raw : text.split("\\r?\\n")) {
            if (raw.trim().isEmpty() || raw.trim().startsWith("#")) continue;

            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == '\t') indent++;
            String line = raw.trim();
            String[] parts = line.split("\\s+");
            if (parts.length == 0) continue;

            while (classStack.size() > indent) {
                classStack.pop();
                mappedClassStack.pop();
            }

            if (parts[0].equals("CLASS")) {
                String oldName = parts.length > 1 ? parts[1] : null;
                String newName = parts.length > 2 ? parts[2] : oldName;
                if (oldName == null) continue;

                String outerOld = classStack.isEmpty() ? "" : classStack.peek() + "$";
                String outerNew = mappedClassStack.isEmpty() ? "" : mappedClassStack.peek() + "$";

                String fullOld = outerOld + oldName;
                String fullNew = outerNew + newName;
                mappings.putClass(fullOld, fullNew);
                classStack.push(fullOld);
                mappedClassStack.push(fullNew);

            } else if (parts[0].equals("FIELD") && parts.length >= 3) {
                String owner = classStack.isEmpty() ? "" : classStack.peek();
                String oldFieldName = parts[1];
                String desc = parts[2];
                String newFieldName = parts.length >= 4 ? parts[3] : oldFieldName;
                mappings.putField(owner, oldFieldName, desc, newFieldName);

            } else if (parts[0].equals("METHOD") && parts.length >= 3) {
                String owner = classStack.isEmpty() ? "" : classStack.peek();
                String oldMethodName = parts[1];
                String desc = parts[2];
                String newMethodName = parts.length >= 4 ? parts[3] : oldMethodName;
                mappings.putMethod(owner, oldMethodName, desc, newMethodName);
            }
        }
        return mappings;
    }

    @Override
    public String export(MappingSet mappings) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : mappings.getClassMappings().entrySet()) {
            sb.append("CLASS ").append(e.getKey()).append(" ").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getFieldMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("\tFIELD ").append(k.getName()).append(" ").append(k.getDesc())
              .append(" ").append(e.getValue()).append("\n");
        }
        for (Map.Entry<dev.joyel.mapping.MemberKey, String> e : mappings.getMethodMappings().entrySet()) {
            dev.joyel.mapping.MemberKey k = e.getKey();
            sb.append("\tMETHOD ").append(k.getName()).append(" ").append(k.getDesc())
              .append(" ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }
}
