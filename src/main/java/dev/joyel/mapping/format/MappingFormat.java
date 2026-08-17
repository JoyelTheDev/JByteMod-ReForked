package dev.joyel.mapping.format;

import dev.joyel.mapping.MappingSet;

public interface MappingFormat {

    String getName();

    String getFileExtension();

    MappingSet parse(String text) throws MappingParseException;

    String export(MappingSet mappings);
}
