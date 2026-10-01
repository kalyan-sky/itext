package com.kalyansky.pdf.convert;

import com.kalyansky.pdf.PdfGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Picks the converter for an uploaded file by its extension and runs it.
 */
public class DocumentConversionService {

    private final Map<String, DocumentConverter> byExtension = new LinkedHashMap<>();

    public DocumentConversionService(List<DocumentConverter> converters) {
        for (DocumentConverter converter : converters) {
            for (String extension : converter.extensions()) {
                if (byExtension.putIfAbsent(extension, converter) != null) {
                    throw new IllegalArgumentException("Two converters handle ." + extension);
                }
            }
        }
    }

    /** Service with every built-in converter. */
    public static DocumentConversionService withDefaults() {
        PdfGenerator generator = new PdfGenerator();
        return new DocumentConversionService(List.of(
                new DocxConverter(generator),
                new HtmlConverter(),
                new TextConverter(generator),
                new CsvConverter(generator),
                new ImageConverter(generator)));
    }

    public Set<String> supportedExtensions() {
        return new TreeSet<>(byExtension.keySet());
    }

    public byte[] convert(SourceDocument source) {
        if (source.data().length == 0) {
            throw ConversionException.unsupported("The file is empty");
        }
        DocumentConverter converter = byExtension.get(source.extension());
        if (converter == null) {
            String ext = source.extension().isEmpty() ? "files without an extension" : "." + source.extension() + " files";
            throw ConversionException.unsupported("Cannot convert " + ext
                    + ". Supported: " + String.join(", ", supportedExtensions()));
        }
        try {
            return converter.convert(source);
        } catch (ConversionException e) {
            throw e;
        } catch (Exception e) {
            // Corrupt or mislabelled files surface from POI/iText as assorted runtime exceptions
            throw ConversionException.unreadable("Could not read " + source.filename()
                    + ". Is it a valid ." + source.extension() + " file?", e);
        }
    }
}
