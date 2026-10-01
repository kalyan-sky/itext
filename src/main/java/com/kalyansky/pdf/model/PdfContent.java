package com.kalyansky.pdf.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything the generator needs to build one PDF: document metadata plus the body blocks.
 */
public record PdfContent(
        String title,
        String author,
        String subject,
        String keywords,
        boolean pageNumbers,
        List<Block> blocks) {

    public PdfContent {
        Objects.requireNonNull(title, "title");
        blocks = List.copyOf(blocks);
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public static final class Builder {
        private final String title;
        private String author;
        private String subject;
        private String keywords;
        private boolean pageNumbers = true;
        private final List<Block> blocks = new ArrayList<>();

        private Builder(String title) {
            this.title = title;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder keywords(String keywords) {
            this.keywords = keywords;
            return this;
        }

        public Builder pageNumbers(boolean pageNumbers) {
            this.pageNumbers = pageNumbers;
            return this;
        }

        public Builder heading(String text) {
            return heading(text, 1);
        }

        public Builder heading(String text, int level) {
            return add(new Block.Heading(text, level));
        }

        public Builder paragraph(String text) {
            return add(new Block.Paragraph(text));
        }

        public Builder bulletList(List<String> items) {
            return add(new Block.BulletList(items));
        }

        public Builder table(List<String> headers, List<List<String>> rows) {
            return add(new Block.Table(headers, rows));
        }

        public Builder table(List<String> headers, List<List<String>> rows, List<Float> columnWidths) {
            return add(new Block.Table(headers, rows, columnWidths));
        }

        public Builder image(byte[] data) {
            return add(new Block.Image(data, 0));
        }

        public Builder image(byte[] data, float width) {
            return add(new Block.Image(data, width));
        }

        public Builder pageBreak() {
            return add(new Block.PageBreak());
        }

        public Builder add(Block block) {
            blocks.add(Objects.requireNonNull(block, "block"));
            return this;
        }

        public PdfContent build() {
            return new PdfContent(title, author, subject, keywords, pageNumbers, blocks);
        }
    }
}
