package com.kalyansky.pdf;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.List;
import com.itextpdf.layout.element.ListItem;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.kalyansky.pdf.model.Block;
import com.kalyansky.pdf.model.PdfContent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns a {@link PdfContent} into a PDF using iText. iText records itself as the
 * document's producer, so every generated file carries an "iText" Producer entry.
 */
public class PdfGenerator {

    private static final DeviceRgb HEADER_BACKGROUND = new DeviceRgb(0xE8, 0xEE, 0xF5);
    private static final float[] HEADING_SIZES = {20f, 16f, 13f};
    private static final float MARGIN = 36f;

    private final PageSize pageSize;

    public PdfGenerator() {
        this(PageSize.A4);
    }

    public PdfGenerator(PageSize pageSize) {
        this.pageSize = pageSize;
    }

    public byte[] generate(PdfContent content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        generate(content, out);
        return out.toByteArray();
    }

    public void generate(PdfContent content, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            generate(content, out);
        }
    }

    /** Writes the PDF to {@code out}. The stream is left open for the caller to close. */
    public void generate(PdfContent content, OutputStream out) {
        PdfWriter writer = new PdfWriter(out);
        writer.setCloseStream(false);
        PdfDocument pdf = new PdfDocument(writer);
        applyMetadata(pdf.getDocumentInfo(), content);

        // immediateFlush=false keeps pages in memory so page numbers can be stamped at the end
        try (Document document = new Document(pdf, pageSize, false)) {
            // Fonts belong to one PdfDocument, so a fresh one is created per PDF
            PdfFont bold = createBoldFont();
            document.setMargins(MARGIN, MARGIN, MARGIN + 12, MARGIN);
            if (content.titleOnPage()) {
                document.add(new Paragraph(content.title())
                        .setFont(bold)
                        .setFontSize(24)
                        .setMarginBottom(12));
            }
            for (Block block : content.blocks()) {
                render(document, block, bold);
            }
            if (content.pageNumbers()) {
                addPageNumbers(document, pdf);
            }
        }
    }

    private static PdfFont createBoldFont() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void applyMetadata(PdfDocumentInfo info, PdfContent content) {
        info.setTitle(content.title());
        if (content.author() != null) {
            info.setAuthor(content.author());
        }
        if (content.subject() != null) {
            info.setSubject(content.subject());
        }
        if (content.keywords() != null) {
            info.setKeywords(content.keywords());
        }
        info.setCreator("itext-pdf-generator");
    }

    private void render(Document document, Block block, PdfFont bold) {
        switch (block) {
            case Block.Heading heading -> document.add(new Paragraph(heading.text())
                    .setFont(bold)
                    .setFontSize(HEADING_SIZES[heading.level() - 1])
                    .setMarginTop(10)
                    .setMarginBottom(4));
            case Block.Paragraph paragraph -> document.add(new Paragraph(paragraph.text())
                    .setFontSize(11)
                    .setMultipliedLeading(1.3f));
            case Block.BulletList bulletList -> {
                List list = new List().setListSymbol("\u2022").setSymbolIndent(10).setFontSize(11);
                bulletList.items().forEach(item -> list.add(new ListItem(item)));
                document.add(list);
            }
            case Block.Table table -> document.add(buildTable(table, bold));
            case Block.Image image -> document.add(buildImage(document, image));
            case Block.PageBreak ignored -> document.add(new AreaBreak());
        }
    }

    private static Table buildTable(Block.Table spec, PdfFont bold) {
        Table table = spec.columnWidths().isEmpty()
                ? new Table(UnitValue.createPercentArray(spec.headers().size()))
                : new Table(UnitValue.createPercentArray(toArray(spec.columnWidths())));
        table.useAllAvailableWidth().setFontSize(10).setMarginTop(6).setMarginBottom(6);

        for (String header : spec.headers()) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setFont(bold))
                    .setBackgroundColor(HEADER_BACKGROUND));
        }
        for (java.util.List<String> row : spec.rows()) {
            for (String value : row) {
                table.addCell(new Cell().add(new Paragraph(value == null ? "" : value)));
            }
        }
        return table;
    }

    private Image buildImage(Document document, Block.Image spec) {
        Image image = new Image(ImageDataFactory.create(spec.data()));
        float available = pageSize.getWidth() - document.getLeftMargin() - document.getRightMargin();
        if (spec.width() > 0) {
            image.scaleToFit(Math.min(spec.width(), available), Float.MAX_VALUE);
        } else if (image.getImageWidth() > available) {
            image.scaleToFit(available, Float.MAX_VALUE);
        }
        return image.setMarginTop(6).setMarginBottom(6);
    }

    private void addPageNumbers(Document document, PdfDocument pdf) {
        int total = pdf.getNumberOfPages();
        for (int page = 1; page <= total; page++) {
            document.showTextAligned(
                    new Paragraph("Page " + page + " of " + total)
                            .setFontSize(9)
                            .setFontColor(ColorConstants.GRAY),
                    pageSize.getWidth() / 2, MARGIN / 2,
                    page, TextAlignment.CENTER, VerticalAlignment.BOTTOM, 0);
        }
    }

    private static float[] toArray(java.util.List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
