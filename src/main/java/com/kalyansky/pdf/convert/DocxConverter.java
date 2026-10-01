package com.kalyansky.pdf.convert;

import com.kalyansky.pdf.PdfGenerator;
import com.kalyansky.pdf.model.PdfContent;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word (.docx) documents, read with Apache POI and laid out again by {@link PdfGenerator}.
 * Keeps the structure (headings, paragraphs, lists, tables, images, page breaks) and the
 * document title/author; character formatting such as fonts, colours and bold text is not kept.
 */
public class DocxConverter implements DocumentConverter {

    private static final Pattern HEADING_STYLE = Pattern.compile("heading\\s*([1-9])");
    private static final Set<String> SUPPORTED_IMAGE_TYPES =
            Set.of("png", "jpeg", "jpg", "gif", "bmp", "tiff", "tif");

    private final PdfGenerator generator;

    public DocxConverter(PdfGenerator generator) {
        this.generator = generator;
    }

    @Override
    public Set<String> extensions() {
        return Set.of("docx");
    }

    @Override
    public byte[] convert(SourceDocument source) throws IOException {
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(source.data()))) {
            var properties = docx.getProperties().getCoreProperties();
            String title = isBlank(properties.getTitle()) ? source.baseName() : properties.getTitle();

            PdfContent.Builder builder = PdfContent.builder(title).titleOnPage(false);
            if (!isBlank(properties.getCreator())) {
                builder.author(properties.getCreator());
            }
            if (!isBlank(properties.getSubject())) {
                builder.subject(properties.getSubject());
            }

            List<String> listItems = new ArrayList<>();
            for (IBodyElement element : docx.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    addParagraph(docx, paragraph, builder, listItems);
                } else if (element instanceof XWPFTable table) {
                    flushList(builder, listItems);
                    addTable(table, builder);
                }
            }
            flushList(builder, listItems);
            return generator.generate(builder.build());
        }
    }

    private void addParagraph(XWPFDocument docx, XWPFParagraph paragraph,
                              PdfContent.Builder builder, List<String> listItems) {
        String text = paragraph.getText().strip();
        int headingLevel = headingLevel(docx, paragraph);

        if (headingLevel == 0 && isListItem(docx, paragraph) && !text.isEmpty()) {
            listItems.add(text);
            return;
        }
        flushList(builder, listItems);

        if (paragraph.isPageBreak()) {
            builder.pageBreak();
        }
        if (!text.isEmpty()) {
            if (headingLevel > 0) {
                builder.heading(text, Math.min(headingLevel, 3));
            } else {
                builder.paragraph(text);
            }
        }
        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture picture : run.getEmbeddedPictures()) {
                var data = picture.getPictureData();
                // Vector formats (EMF/WMF/SVG) cannot be embedded as raster images, so they are skipped
                if (data != null && SUPPORTED_IMAGE_TYPES.contains(data.suggestFileExtension().toLowerCase(Locale.ROOT))) {
                    builder.image(data.getData());
                }
            }
            if (hasPageBreak(run)) {
                builder.pageBreak();
            }
        }
    }

    private static void addTable(XWPFTable table, PdfContent.Builder builder) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().strip());
            }
            rows.add(cells);
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columns == 0) {
            return;
        }
        for (List<String> row : rows) {
            row.addAll(Collections.nCopies(columns - row.size(), ""));
        }
        builder.table(rows.get(0), rows.subList(1, rows.size()));
    }

    /** 1-9 for "Heading N" styles, 1 for "Title", 0 for everything else. */
    private static int headingLevel(XWPFDocument docx, XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null) {
            return 0;
        }
        // Style ids are localised in some Word versions; the style name is stable
        XWPFStyle style = docx.getStyles() == null ? null : docx.getStyles().getStyle(styleId);
        String name = (style != null && style.getName() != null ? style.getName() : styleId)
                .toLowerCase(Locale.ROOT);
        if (name.equals("title")) {
            return 1;
        }
        Matcher matcher = HEADING_STYLE.matcher(name);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /**
     * List paragraphs carry a numbering reference either directly or through their style
     * (Word's built-in "List Bullet"/"List Number" styles), possibly inherited via basedOn.
     */
    private static boolean isListItem(XWPFDocument docx, XWPFParagraph paragraph) {
        if (paragraph.getNumID() != null) {
            return true;
        }
        if (docx.getStyles() == null) {
            return false;
        }
        String styleId = paragraph.getStyleID();
        // Depth limit guards against basedOn cycles in malformed files
        for (int depth = 0; styleId != null && depth < 10; depth++) {
            XWPFStyle style = docx.getStyles().getStyle(styleId);
            if (style == null) {
                return false;
            }
            var pPr = style.getCTStyle().getPPr();
            if (pPr != null && pPr.isSetNumPr()) {
                return true;
            }
            styleId = style.getBasisStyleID();
        }
        return false;
    }

    private static boolean hasPageBreak(XWPFRun run) {
        for (CTBr br : run.getCTR().getBrList()) {
            if (br.getType() == STBrType.PAGE) {
                return true;
            }
        }
        return false;
    }

    private static void flushList(PdfContent.Builder builder, List<String> listItems) {
        if (!listItems.isEmpty()) {
            builder.bulletList(listItems);
            listItems.clear();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
