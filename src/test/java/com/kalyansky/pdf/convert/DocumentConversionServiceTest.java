package com.kalyansky.pdf.convert;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.util.Units;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentConversionServiceTest {

    private final DocumentConversionService service = DocumentConversionService.withDefaults();

    @Test
    void convertsDocxWithHeadingsListsTablesImagesAndPageBreaks() throws IOException {
        byte[] pdf = service.convert(new SourceDocument("report.docx", sampleDocx()));

        try (PdfDocument document = open(pdf)) {
            assertProducedByIText(document);
            assertEquals("Quarterly Report", document.getDocumentInfo().getTitle());
            assertEquals("Kalyan", document.getDocumentInfo().getAuthor());
            assertEquals(2, document.getNumberOfPages());

            String page1 = PdfTextExtractor.getTextFromPage(document.getPage(1));
            for (String expected : List.of("Overview", "Sales went up.", "first item", "second item",
                    "Region", "Revenue", "North", "120")) {
                assertTrue(page1.contains(expected), "missing '" + expected + "' in:\n" + page1);
            }
            assertEquals(1, document.getPage(1).getResources().getResourceNames(PdfName.XObject).size());
            assertTrue(PdfTextExtractor.getTextFromPage(document.getPage(2)).contains("After the break"));
        }
    }

    @Test
    void docxListsDefinedByParagraphStyleBecomeBullets() throws IOException {
        byte[] docxBytes;
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Like Word's "List Bullet": the numbering lives on the style, not the paragraph
            CTStyle style = CTStyle.Factory.newInstance();
            style.setStyleId("ListBullet");
            style.addNewName().setVal("List Bullet");
            style.setType(STStyleType.PARAGRAPH);
            style.addNewPPr().addNewNumPr().addNewNumId().setVal(BigInteger.ONE);
            docx.createStyles().addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(style));

            XWPFParagraph item = docx.createParagraph();
            item.setStyle("ListBullet");
            item.createRun().setText("styled item");
            docx.createParagraph().createRun().setText("plain text");
            docx.write(out);
            docxBytes = out.toByteArray();
        }

        try (PdfDocument document = open(service.convert(new SourceDocument("list.docx", docxBytes)))) {
            String text = PdfTextExtractor.getTextFromPage(document.getPage(1));
            assertTrue(text.contains("\u2022 styled item"), text);
            assertFalse(text.contains("\u2022 plain text"), text);
        }
    }

    @Test
    void convertsHtml() throws IOException {
        String html = "<html><head><title>Hello page</title></head>"
                + "<body><h1>Hello</h1><p>From <b>HTML</b></p></body></html>";
        byte[] pdf = service.convert(new SourceDocument("page.html", html.getBytes(StandardCharsets.UTF_8)));

        try (PdfDocument document = open(pdf)) {
            assertProducedByIText(document);
            String text = PdfTextExtractor.getTextFromPage(document.getPage(1));
            assertTrue(text.contains("Hello") && text.contains("From HTML"), text);
        }
    }

    @Test
    void htmlCannotLoadLocalFiles(@TempDir Path dir) throws IOException {
        Path image = Files.write(dir.resolve("secret.png"), png(10, 10));
        String html = "<html><body><p>x</p><img src=\"" + image.toUri() + "\"/>"
                + "<img src=\"data:image/png;base64," + Base64.getEncoder().encodeToString(png(10, 10)) + "\"/>"
                + "</body></html>";
        byte[] pdf = service.convert(new SourceDocument("page.html", html.getBytes(StandardCharsets.UTF_8)));

        try (PdfDocument document = open(pdf)) {
            // Only the inline data: image is embedded; the file on disk is not read
            assertEquals(1, document.getPage(1).getResources().getResourceNames(PdfName.XObject).size());
        }
    }

    @Test
    void convertsTextKeepingParagraphs() throws IOException {
        String text = "First paragraph\nstill first\n\nSecond paragraph";
        byte[] pdf = service.convert(new SourceDocument("notes.txt", text.getBytes(StandardCharsets.UTF_8)));

        try (PdfDocument document = open(pdf)) {
            assertProducedByIText(document);
            assertEquals("notes", document.getDocumentInfo().getTitle());
            String extracted = PdfTextExtractor.getTextFromPage(document.getPage(1));
            assertTrue(extracted.contains("still first") && extracted.contains("Second paragraph"), extracted);
        }
    }

    @Test
    void decodesWindows1252Text() throws IOException {
        byte[] data = "Café prices".getBytes(Charset.forName("windows-1252"));
        byte[] pdf = service.convert(new SourceDocument("menu.txt", data));

        try (PdfDocument document = open(pdf)) {
            assertTrue(PdfTextExtractor.getTextFromPage(document.getPage(1)).contains("Café prices"));
        }
    }

    @Test
    void convertsCsvToTable() throws IOException {
        String csv = "Name,Note\nWidget,\"Has, a comma\"\nGadget\n";
        byte[] pdf = service.convert(new SourceDocument("items.csv", csv.getBytes(StandardCharsets.UTF_8)));

        try (PdfDocument document = open(pdf)) {
            assertProducedByIText(document);
            String text = PdfTextExtractor.getTextFromPage(document.getPage(1));
            assertTrue(text.contains("Has, a comma") && text.contains("Gadget"), text);
        }
    }

    @Test
    void parsesQuotedCsvFields() {
        assertEquals(List.of(List.of("a", "b \"quoted\"", "multi\nline"), List.of("c")),
                CsvConverter.parse("a,\"b \"\"quoted\"\"\",\"multi\nline\"\r\n\r\nc"));
    }

    @Test
    void convertsImages() throws IOException {
        byte[] pdf = service.convert(new SourceDocument("photo.PNG", png(200, 100)));

        try (PdfDocument document = open(pdf)) {
            assertProducedByIText(document);
            assertEquals(1, document.getPage(1).getResources().getResourceNames(PdfName.XObject).size());
        }
    }

    @Test
    void rejectsUnsupportedFormats() {
        ConversionException e = assertThrows(ConversionException.class,
                () -> service.convert(new SourceDocument("archive.zip", new byte[] {1})));
        assertTrue(e.isUnsupportedFormat());
        assertTrue(e.getMessage().contains(".zip"), e.getMessage());
    }

    @Test
    void reportsCorruptFilesAsUnreadable() {
        ConversionException e = assertThrows(ConversionException.class,
                () -> service.convert(new SourceDocument("broken.docx", "not a zip".getBytes())));
        assertFalse(e.isUnsupportedFormat());
    }

    @Test
    void sourceDocumentStripsClientPaths() {
        SourceDocument source = new SourceDocument("C:\\Users\\me\\My Report.DOCX", new byte[0]);
        assertEquals("My Report.DOCX", source.filename());
        assertEquals("docx", source.extension());
        assertEquals("My Report", source.baseName());
    }

    private static byte[] sampleDocx() throws IOException {
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            docx.getProperties().getCoreProperties().setTitle("Quarterly Report");
            docx.getProperties().getCoreProperties().setCreator("Kalyan");
            addHeadingStyle(docx);

            XWPFParagraph heading = docx.createParagraph();
            heading.setStyle("Heading1");
            heading.createRun().setText("Overview");

            docx.createParagraph().createRun().setText("Sales went up.");

            // Word marks list paragraphs with a numbering reference; the converter only checks it is present
            for (String item : List.of("first item", "second item")) {
                XWPFParagraph listItem = docx.createParagraph();
                listItem.setNumID(BigInteger.ONE);
                listItem.createRun().setText(item);
            }

            XWPFTable table = docx.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Region");
            table.getRow(0).getCell(1).setText("Revenue");
            table.getRow(1).getCell(0).setText("North");
            table.getRow(1).getCell(1).setText("120");

            var imageRun = docx.createParagraph().createRun();
            try {
                imageRun.addPicture(new ByteArrayInputStream(png(40, 20)), Document.PICTURE_TYPE_PNG,
                        "dot.png", Units.toEMU(40), Units.toEMU(20));
            } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
                throw new IOException(e);
            }
            imageRun.addBreak(BreakType.PAGE);

            docx.createParagraph().createRun().setText("After the break");
            docx.write(out);
            return out.toByteArray();
        }
    }

    private static void addHeadingStyle(XWPFDocument docx) {
        CTStyle style = CTStyle.Factory.newInstance();
        style.setStyleId("Heading1");
        style.addNewName().setVal("heading 1");
        style.setType(STStyleType.PARAGRAPH);
        docx.createStyles().addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(style));
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static void assertProducedByIText(PdfDocument document) {
        String producer = document.getDocumentInfo().getProducer();
        assertTrue(producer.contains("iText"), "producer was " + producer);
    }

    private static PdfDocument open(byte[] pdf) throws IOException {
        return new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)));
    }
}
