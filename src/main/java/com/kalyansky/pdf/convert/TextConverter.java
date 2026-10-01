package com.kalyansky.pdf.convert;

import com.kalyansky.pdf.PdfGenerator;
import com.kalyansky.pdf.model.PdfContent;

import java.util.Set;

/**
 * Plain text, laid out exactly as in the file: same lines, spaces, indentation and blank
 * lines, in a monospace font.
 */
public class TextConverter implements DocumentConverter {

    private final PdfGenerator generator;

    public TextConverter(PdfGenerator generator) {
        this.generator = generator;
    }

    @Override
    public Set<String> extensions() {
        return Set.of("txt", "text", "log");
    }

    @Override
    public byte[] convert(SourceDocument source) {
        String text = TextDecoding.decode(source.data()).replace("\r\n", "\n").replace('\r', '\n');
        PdfContent content = PdfContent.builder(source.baseName())
                .titleOnPage(false)
                .pageNumbers(false)
                .preformatted(text)
                .build();
        return generator.generate(content);
    }
}
