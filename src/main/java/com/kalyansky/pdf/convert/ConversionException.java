package com.kalyansky.pdf.convert;

/**
 * The file could not be converted: unsupported format, or content that could not be read.
 */
public class ConversionException extends RuntimeException {

    private final boolean unsupportedFormat;

    private ConversionException(String message, Throwable cause, boolean unsupportedFormat) {
        super(message, cause);
        this.unsupportedFormat = unsupportedFormat;
    }

    public static ConversionException unsupported(String message) {
        return new ConversionException(message, null, true);
    }

    public static ConversionException unreadable(String message, Throwable cause) {
        return new ConversionException(message, cause, false);
    }

    public boolean isUnsupportedFormat() {
        return unsupportedFormat;
    }
}
