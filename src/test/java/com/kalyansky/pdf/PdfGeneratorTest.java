package com.kalyansky.pdf;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.kalyansky.pdf.model.Block;
import com.kalyansky.pdf.model.PdfContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfGeneratorTest {

    private final PdfGenerator generator = new PdfGenerator();

    @Test
    void producerIsITextAndMetadataIsSet() throws IOException {
        byte[] bytes = generator.generate(PdfContent.builder("Invoice")
                .author("Kalyan")
                .subject("Test subject")
                .paragraph("Hello")
                .build());

        try (PdfDocument pdf = open(bytes)) {
            PdfDocumentInfo info = pdf.getDocumentInfo();
            assertTrue(info.getProducer().contains("iText"), "producer was " + info.getProducer());
            assertEquals("Invoice", info.getTitle());
            assertEquals("Kalyan", info.getAuthor());
            assertEquals("Test subject", info.getSubject());
        }
    }

    @Test
    void rendersAllBlockTypesAsText() throws IOException {
        byte[] bytes = generator.generate(PdfContent.builder("Report")
                .heading("Section A")
                .paragraph("Some body text")
                .bulletList(List.of("first point", "second point"))
                .table(List.of("Name", "Qty"), List.of(List.of("Widget", "7")))
                .build());

        try (PdfDocument pdf = open(bytes)) {
            String text = PdfTextExtractor.getTextFromPage(pdf.getPage(1));
            for (String expected : List.of("Report", "Section A", "Some body text",
                    "first point", "second point", "Name", "Qty", "Widget", "7", "Page 1 of 1")) {
                assertTrue(text.contains(expected), "missing '" + expected + "' in:\n" + text);
            }
        }
    }

    @Test
    void pageBreakAddsPageAndNumbersEveryPage() throws IOException {
        byte[] bytes = generator.generate(PdfContent.builder("Two pages")
                .paragraph("one")
                .pageBreak()
                .paragraph("two")
                .build());

        try (PdfDocument pdf = open(bytes)) {
            assertEquals(2, pdf.getNumberOfPages());
            assertTrue(PdfTextExtractor.getTextFromPage(pdf.getPage(2)).contains("Page 2 of 2"));
        }
    }

    @Test
    void pageNumbersCanBeDisabled() throws IOException {
        byte[] bytes = generator.generate(PdfContent.builder("No numbers")
                .pageNumbers(false)
                .paragraph("body")
                .build());

        try (PdfDocument pdf = open(bytes)) {
            String text = PdfTextExtractor.getTextFromPage(pdf.getPage(1));
            assertTrue(!text.contains("Page 1 of 1"), text);
        }
    }

    @Test
    void writesToFileCreatingParentDirectories(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("nested/out.pdf");
        generator.generate(PdfContent.builder("File").paragraph("x").build(), target);

        byte[] bytes = Files.readAllBytes(target);
        assertTrue(new String(bytes, 0, 5).startsWith("%PDF-"));
    }

    @Test
    void embedsImages() throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", png);

        byte[] bytes = generator.generate(PdfContent.builder("With image")
                .image(png.toByteArray(), 100)
                .build());

        try (PdfDocument pdf = open(bytes)) {
            assertEquals(1, pdf.getPage(1).getResources().getResourceNames(
                    PdfName.XObject).size());
        }
    }

    @Test
    void tableRowsMustMatchHeaderCount() {
        assertThrows(IllegalArgumentException.class, () ->
                new Block.Table(List.of("A", "B"), List.of(List.of("only one"))));
    }

    private static PdfDocument open(byte[] bytes) throws IOException {
        return new PdfDocument(new PdfReader(new ByteArrayInputStream(bytes)));
    }
}
