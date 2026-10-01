package com.kalyansky.pdf.convert;

/**
 * The file could not be converted: unsupported format, content that could not be read,
 * or a converter that is not available on this server.
 */
public class ConversionException extends RuntimeException {

    public enum Kind {
        UNSUPPORTED_FORMAT,
        UNREADABLE,
        UNAVAILABLE
    }

    private final Kind kind;

    private ConversionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public static ConversionException unsupported(String message) {
        return new ConversionException(Kind.UNSUPPORTED_FORMAT, message, null);
    }

    public static ConversionException unreadable(String message, Throwable cause) {
        return new ConversionException(Kind.UNREADABLE, message, cause);
    }

    public static ConversionException unavailable(String message, Throwable cause) {
        return new ConversionException(Kind.UNAVAILABLE, message, cause);
    }

    public Kind kind() {
        return kind;
    }

    public boolean isUnsupportedFormat() {
        return kind == Kind.UNSUPPORTED_FORMAT;
    }
}
