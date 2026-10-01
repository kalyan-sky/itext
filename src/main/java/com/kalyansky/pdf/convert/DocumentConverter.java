package com.kalyansky.pdf.convert;

import java.io.IOException;
import java.util.Set;

/**
 * Converts one family of file formats to PDF. Every implementation writes the PDF with
 * iText, so the output's Producer entry is always iText.
 */
public interface DocumentConverter {

    /** Lower-case file extensions (without the dot) this converter accepts. */
    Set<String> extensions();

    byte[] convert(SourceDocument source) throws IOException;
}
