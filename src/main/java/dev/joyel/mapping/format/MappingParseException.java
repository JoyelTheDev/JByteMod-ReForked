package dev.joyel.mapping.format;

public class MappingParseException extends Exception {

    public MappingParseException(String message) {
        super(message);
    }

    public MappingParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
