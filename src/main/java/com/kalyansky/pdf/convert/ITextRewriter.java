package com.kalyansky.pdf.convert;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Writes an existing PDF out again as a new iText document. Pages are copied object for
 * object (content streams, fonts, images, links), so they look exactly the same, while the
 * file itself is produced by iText and carries iText as its Producer.
 */
final class ITextRewriter {

    private ITextRewriter() {
    }

    static byte[] rewrite(byte[] pdf, String fallbackTitle) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PdfDocument source = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)));
             PdfDocument target = new PdfDocument(new PdfWriter(out))) {
            // Must be set before copying so bookmarks come along with the pages
            target.initializeOutlines();
            source.copyPagesTo(1, source.getNumberOfPages(), target);
            copyInfo(source.getDocumentInfo(), target.getDocumentInfo(), fallbackTitle);
        }
        return out.toByteArray();
    }

    private static void copyInfo(PdfDocumentInfo from, PdfDocumentInfo to, String fallbackTitle) {
        to.setTitle(isBlank(from.getTitle()) ? fallbackTitle : from.getTitle());
        if (!isBlank(from.getAuthor())) {
            to.setAuthor(from.getAuthor());
        }
        if (!isBlank(from.getSubject())) {
            to.setSubject(from.getSubject());
        }
        if (!isBlank(from.getKeywords())) {
            to.setKeywords(from.getKeywords());
        }
        if (!isBlank(from.getCreator())) {
            to.setCreator(from.getCreator());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
