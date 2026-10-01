package com.kalyansky.pdf.convert;

import com.kalyansky.pdf.PdfGenerator;
import com.kalyansky.pdf.model.PdfContent;

import java.util.Set;

/**
 * A single image placed on an A4 page, scaled down when it is wider than the page.
 */
public class ImageConverter implements DocumentConverter {

    private final PdfGenerator generator;

    public ImageConverter(PdfGenerator generator) {
        this.generator = generator;
    }

    @Override
    public Set<String> extensions() {
        return Set.of("png", "jpg", "jpeg", "gif", "bmp", "tif", "tiff");
    }

    @Override
    public byte[] convert(SourceDocument source) {
        PdfContent content = PdfContent.builder(source.baseName())
                .titleOnPage(false)
                .pageNumbers(false)
                .image(source.data())
                .build();
        return generator.generate(content);
    }
}
