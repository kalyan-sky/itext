# itext

A small PDF generator built on [iText](https://itextpdf.com/) (Core 9.8.0). You describe
a document as headings, paragraphs, lists, tables, images and page breaks, and
`PdfGenerator` turns it into a PDF. Because iText writes the file, every generated PDF
carries an `iText` **Producer** entry in its metadata.

## Requirements

- Java 21+
- Maven 3.9+

## Usage

```java
PdfContent content = PdfContent.builder("Monthly Sales Report")
        .author("Kalyan")
        .subject("Sales summary")
        .heading("Summary")
        .paragraph("Revenue grew 12% compared to last month.")
        .bulletList(List.of("Three new customers", "Average order value: $245"))
        .heading("Orders", 2)
        .table(List.of("Order #", "Customer", "Total"),
               List.of(List.of("1001", "Acme Corp", "$540.00"),
                       List.of("1002", "Globex", "$120.00")))
        .build();

PdfGenerator generator = new PdfGenerator();          // A4 by default
generator.generate(content, Path.of("report.pdf"));   // to a file
byte[] pdf = generator.generate(content);             // or in memory
generator.generate(content, outputStream);            // or to any stream (left open)
```

### Supported blocks

| Builder method | Renders |
| --- | --- |
| `heading(text)` / `heading(text, level)` | Bold heading, levels 1-3 |
| `paragraph(text)` | Body text |
| `bulletList(items)` | Bulleted list |
| `table(headers, rows)` / `table(headers, rows, widths)` | Table with a shaded header row; widths are relative weights |
| `image(bytes)` / `image(bytes, width)` | PNG/JPEG image, scaled down to fit the page |
| `pageBreak()` | Starts a new page |

Document options: `author`, `subject`, `keywords`, and `pageNumbers(false)` to turn off the
"Page X of Y" footer. Use `new PdfGenerator(PageSize.LETTER)` for other page sizes.

## Run the example

```sh
mvn compile exec:java                                   # writes output/example.pdf
mvn compile exec:java -Dexec.args="out/report.pdf"      # custom path
```

## Tests

```sh
mvn test
```

The tests read the generated PDFs back with iText and check the Producer metadata,
the rendered text, page breaks and page numbering.

## License note

iText Core is licensed under the AGPL. Using it in closed-source software requires a
commercial license from Apryse.
