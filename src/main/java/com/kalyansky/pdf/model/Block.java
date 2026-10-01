package com.kalyansky.pdf.model;

import java.util.List;
import java.util.Objects;

/**
 * A piece of content placed in the document body, rendered top to bottom.
 */
public sealed interface Block
        permits Block.Heading, Block.Paragraph, Block.Preformatted, Block.BulletList, Block.Table,
                Block.Image, Block.PageBreak {

    /** Section heading; level 1 is the largest, level 3 the smallest. */
    record Heading(String text, int level) implements Block {
        public Heading {
            Objects.requireNonNull(text, "text");
            if (level < 1 || level > 3) {
                throw new IllegalArgumentException("Heading level must be 1-3, got " + level);
            }
        }
    }

    record Paragraph(String text) implements Block {
        public Paragraph {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Text shown exactly as given in a monospace font: every space, tab and line break is kept. */
    record Preformatted(String text) implements Block {
        public Preformatted {
            Objects.requireNonNull(text, "text");
        }
    }

    record BulletList(List<String> items) implements Block {
        public BulletList {
            items = List.copyOf(items);
        }
    }

    /**
     * Table with a header row. Every row must have as many cells as there are headers.
     * Column widths are relative weights; pass an empty list for equal widths.
     */
    record Table(List<String> headers, List<List<String>> rows, List<Float> columnWidths)
            implements Block {
        public Table {
            headers = List.copyOf(headers);
            rows = rows.stream().map(List::copyOf).toList();
            columnWidths = List.copyOf(columnWidths);
            if (headers.isEmpty()) {
                throw new IllegalArgumentException("Table needs at least one column");
            }
            for (List<String> row : rows) {
                if (row.size() != headers.size()) {
                    throw new IllegalArgumentException("Row has " + row.size()
                            + " cells but the table has " + headers.size() + " columns");
                }
            }
            if (!columnWidths.isEmpty() && columnWidths.size() != headers.size()) {
                throw new IllegalArgumentException("Expected " + headers.size()
                        + " column widths, got " + columnWidths.size());
            }
        }

        public Table(List<String> headers, List<List<String>> rows) {
            this(headers, rows, List.of());
        }
    }

    /**
     * Image from raw bytes (PNG, JPEG, ...). A width of 0 scales it to the page width
     * when it is wider than the page, and keeps its natural size otherwise.
     */
    record Image(byte[] data, float width) implements Block {
        public Image {
            Objects.requireNonNull(data, "data");
            if (width < 0) {
                throw new IllegalArgumentException("width must be >= 0");
            }
        }
    }

    record PageBreak() implements Block {
    }
}
