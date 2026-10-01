package com.kalyansky.pdf.convert;

import java.util.Locale;
import java.util.Objects;

/**
 * An uploaded file waiting to be converted.
 */
public record SourceDocument(String filename, byte[] data) {

    public SourceDocument {
        Objects.requireNonNull(data, "data");
        filename = filename == null || filename.isBlank() ? "document" : stripPath(filename);
    }

    /** Lower-case extension without the dot, or an empty string when there is none. */
    public String extension() {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** File name without its extension, used as the PDF title and download name. */
    public String baseName() {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }

    // Browsers may send a full client path; only the last segment is meaningful
    private static String stripPath(String name) {
        return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }
}
