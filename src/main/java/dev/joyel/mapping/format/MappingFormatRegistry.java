package dev.joyel.mapping.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MappingFormatRegistry {

    private static final List<MappingFormat> FORMATS = new ArrayList<MappingFormat>();

    static {
        FORMATS.add(new ProguardMappingFormat());
        FORMATS.add(new SrgMappingFormat());
        FORMATS.add(new TinyV1MappingFormat());
        FORMATS.add(new EnigmaMappingFormat());
        FORMATS.add(new SimpleMappingFormat());
    }

    private MappingFormatRegistry() {}

    public static List<MappingFormat> getFormats() {
        return Collections.unmodifiableList(FORMATS);
    }

    public static MappingFormat byName(String name) {
        for (MappingFormat f : FORMATS) {
            if (f.getName().equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    public static MappingFormat byExtension(String ext) {
        for (MappingFormat f : FORMATS) {
            if (f.getFileExtension().equalsIgnoreCase(ext)) return f;
        }
        return null;
    }

    public static void register(MappingFormat format) {
        FORMATS.add(format);
    }
}
