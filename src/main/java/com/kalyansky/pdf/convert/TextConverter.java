package com.kalyansky.pdf.convert;

import com.kalyansky.pdf.PdfGenerator;
import com.kalyansky.pdf.model.PdfContent;

import java.util.Set;

/**
 * Plain text: blank lines separate paragraphs, single line breaks are kept.
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
        PdfContent.Builder builder = PdfContent.builder(source.baseName()).titleOnPage(false);
        for (String paragraph : text.split("\n\\s*\n")) {
            if (!paragraph.isBlank()) {
                builder.paragraph(paragraph.stripTrailing());
            }
        }
        return generator.generate(builder.build());
    }
}
