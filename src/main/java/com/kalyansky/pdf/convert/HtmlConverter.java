package com.kalyansky.pdf.convert;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.styledxmlparser.resolver.resource.IResourceRetriever;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;

/**
 * HTML with CSS, rendered by iText pdfHTML.
 */
public class HtmlConverter implements DocumentConverter {

    /**
     * Uploaded HTML is untrusted, so it may not pull in local files or remote URLs
     * (which would let an upload read server files or probe the internal network).
     * Inline data: URIs are resolved by iText without the retriever and keep working.
     */
    private static final IResourceRetriever NO_EXTERNAL_RESOURCES = new IResourceRetriever() {
        @Override
        public InputStream getInputStreamByUrl(URL url) {
            return null;
        }

        @Override
        public byte[] getByteArrayByUrl(URL url) {
            return null;
        }
    };

    @Override
    public Set<String> extensions() {
        return Set.of("html", "htm");
    }

    @Override
    public byte[] convert(SourceDocument source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdf = new PdfDocument(new PdfWriter(out));
        // A <title> in the HTML takes precedence over this
        pdf.getDocumentInfo().setTitle(source.baseName());

        ConverterProperties properties = new ConverterProperties()
                .setResourceRetriever(NO_EXTERNAL_RESOURCES);
        com.itextpdf.html2pdf.HtmlConverter.convertToPdf(
                new ByteArrayInputStream(source.data()), pdf, properties);
        return out.toByteArray();
    }
}
