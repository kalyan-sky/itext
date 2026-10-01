package com.kalyansky.pdf.convert;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.EventType;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData;
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentConversionServiceTest {

    private final DocumentConversionService service = DocumentConversionService.withDefaults();

    /** Office conversion needs LibreOffice; these tests are skipped on machines without it. */
    static boolean libreOfficeInstalled() {
        try {
            Process process = new ProcessBuilder("soffice", "--version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("libreOfficeInstalled")
    void docxKeepsOriginalLayoutFontsAndColours() throws IOException {
        byte[] pdf = service.convert(new SourceDocument("report.docx", formattedDocx()));

        try (PdfDocument document = open(pdf)) {
            assertProducedByIText(document);
            assertEquals("Quarterly Report", document.getDocumentInfo().getTitle());
            assertEquals("Kalyan", document.getDocumentInfo().getAuthor());

            // Page setup comes from the document: US Letter landscape, two pages
            assertEquals(2, document.getNumberOfPages());
            Rectangle size = document.getPage(1).getPageSize();
            assertEquals(792, size.getWidth(), 1);
            assertEquals(612, size.getHeight(), 1);

            String page1 = PdfTextExtractor.getTextFromPage(document.getPage(1));
            for (String expected : List.of("Big red title", "Body text", "Region", "North", "120")) {
                assertTrue(page1.contains(expected), "missing '" + expected + "' in:\n" + page1);
            }
            assertEquals(1, document.getPage(1).getResources().getResourceNames(PdfName.XObject).size());
            assertTrue(PdfTextExtractor.getTextFromPage(document.getPage(2)).contains("After the break"));

            // Character formatting is kept: bold serif, red, much larger than the body text
            List<TextRenderInfo> runs = textRuns(document);
            TextRenderInfo title = find(runs, "Big");
            TextRenderInfo body = find(runs, "Body");
            String titleFont = title.getFont().getFontProgram().getFontNames().getFontName();
            assertTrue(titleFont.contains("Bold") && titleFont.contains("Serif"), titleFont);
            assertArrayEquals(new float[] {1, 0, 0}, title.getFillColor().getColorValue(), 0.01f);
            assertTrue(height(title) > 2 * height(body),
                    "title " + height(title) + " vs body " + height(body));
        }
    }

    @Test
    void rejectsOfficeFilesWithWrongContent() {
        for (String name : List.of("broken.docx", "broken.doc", "broken.rtf", "broken.odt")) {
            ConversionException e = assertThrows(ConversionException.class,
                    () -> service.convert(new SourceDocument(name, "just some text".getBytes())));
            assertEquals(ConversionException.Kind.UNREADABLE, e.kind(), name);
        }
    }

    @Test
    void supportsOfficeFormats() {
        assertTrue(service.supportedExtensions().containsAll(
                Set.of("docx", "doc", "odt", "rtf", "xlsx", "xls", "ods", "pptx", "ppt", "odp")));
    }

    @Test
    void reportsMissingLibreOffice() {
        DocumentConversionService withoutOffice = new DocumentConversionService(
                List.of(new OfficeConverter("/nonexistent/soffice", 1)));
        ConversionException e = assertThrows(ConversionException.class,
                () -> withoutOffice.convert(new SourceDocument("a.docx", new byte[] {'P', 'K', 3, 4, 0})));
        assertEquals(ConversionException.Kind.UNAVAILABLE, e.kind());
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
    void textKeepsLinesSpacingAndIndentationExactly() throws IOException {
        String text = "Header line\n    indented by four\n\tindented by tab\n\n\nafter two blank lines";
        byte[] pdf = service.convert(new SourceDocument("notes.txt", text.getBytes(StandardCharsets.UTF_8)));

        try (PdfDocument document = open(pdf)) {
            assertProducedByIText(document);
            assertEquals("notes", document.getDocumentInfo().getTitle());
            String extracted = PdfTextExtractor.getTextFromPage(document.getPage(1)).replace('\u00A0', ' ');
            assertEquals(List.of("Header line", "    indented by four", "        indented by tab",
                            "", "", "after two blank lines"),
                    extracted.lines().map(String::stripTrailing).toList());

            // Nothing is added: no title line and no page-number footer
            assertFalse(extracted.contains("Page 1"), extracted);
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
            assertFalse(text.contains("items") || text.contains("Page 1"), text);
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
                () -> service.convert(new SourceDocument("broken.png", "not an image".getBytes())));
        assertEquals(ConversionException.Kind.UNREADABLE, e.kind());
    }

    @Test
    void sourceDocumentStripsClientPaths() {
        SourceDocument source = new SourceDocument("C:\\Users\\me\\My Report.DOCX", new byte[0]);
        assertEquals("My Report.DOCX", source.filename());
        assertEquals("docx", source.extension());
        assertEquals("My Report", source.baseName());
    }

    /** Landscape Letter document with a big bold red serif title, body text, table, image and page break. */
    private static byte[] formattedDocx() throws IOException {
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            docx.getProperties().getCoreProperties().setTitle("Quarterly Report");
            docx.getProperties().getCoreProperties().setCreator("Kalyan");

            CTPageSz pageSize = docx.getDocument().getBody().addNewSectPr().addNewPgSz();
            pageSize.setW(BigInteger.valueOf(15840)); // 11in in twentieths of a point
            pageSize.setH(BigInteger.valueOf(12240)); // 8.5in
            pageSize.setOrient(STPageOrientation.LANDSCAPE);

            XWPFRun title = docx.createParagraph().createRun();
            title.setText("Big red title");
            title.setBold(true);
            title.setFontSize(28);
            title.setColor("FF0000");
            title.setFontFamily("Liberation Serif");

            XWPFRun body = docx.createParagraph().createRun();
            body.setText("Body text");
            body.setFontSize(11);
            body.setFontFamily("Liberation Sans");

            XWPFTable table = docx.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Region");
            table.getRow(0).getCell(1).setText("Revenue");
            table.getRow(1).getCell(0).setText("North");
            table.getRow(1).getCell(1).setText("120");

            XWPFRun imageRun = docx.createParagraph().createRun();
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

    private static List<TextRenderInfo> textRuns(PdfDocument document) {
        List<TextRenderInfo> runs = new ArrayList<>();
        new PdfCanvasProcessor(new IEventListener() {
            @Override
            public void eventOccurred(IEventData data, EventType type) {
                if (data instanceof TextRenderInfo info) {
                    info.preserveGraphicsState();
                    runs.add(info);
                }
            }

            @Override
            public Set<EventType> getSupportedEvents() {
                return Set.of(EventType.RENDER_TEXT);
            }
        }).processPageContent(document.getPage(1));
        return runs;
    }

    private static TextRenderInfo find(List<TextRenderInfo> runs, String prefix) {
        return runs.stream()
                .filter(info -> info.getText().startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No text starting with " + prefix + " in "
                        + runs.stream().map(TextRenderInfo::getText).toList()));
    }

    private static float height(TextRenderInfo info) {
        return info.getAscentLine().getStartPoint().get(1) - info.getDescentLine().getStartPoint().get(1);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static void assertProducedByIText(PdfDocument document) {
        String producer = document.getDocumentInfo().getProducer();
        assertTrue(producer.startsWith("iText"), "producer was " + producer);
    }

    private static PdfDocument open(byte[] pdf) throws IOException {
        return new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)));
    }
}
