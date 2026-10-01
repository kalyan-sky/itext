# itext

PDF generation built on [iText](https://itextpdf.com/) (Core 9.8.0). Every PDF this
project writes, whether it comes from code or from an uploaded document, is produced by
iText, so its **Producer** metadata reads `iText® Core 9.8.0 ...`.

- **Web UI**: upload a document in the browser and get it back as a PDF.
- **Java API**: describe a document in code with `PdfContent` and render it with `PdfGenerator`.

## Requirements

- Java 21+
- Maven 3.9+

## Web UI: convert documents to PDF

```sh
mvn spring-boot:run
# or
mvn package && java -jar target/itext-pdf-generator-1.0.0-SNAPSHOT.jar
```

Open http://localhost:8080, drop a file on the page (or browse for one) and click
**Convert to PDF**. The page previews the PDF, shows its Producer, and lets you download it.
Set `PORT` to use a different port. Uploads are limited to 20 MB.

| Format | Extensions | How it is converted |
| --- | --- | --- |
| Word | `.docx` | Read with Apache POI. Headings, paragraphs, bullet/numbered lists, tables, PNG/JPEG/GIF/BMP/TIFF images, page breaks, title and author are kept, then laid out by `PdfGenerator` |
| HTML | `.html`, `.htm` | Rendered with iText pdfHTML, including CSS. External files and URLs are not loaded; inline `data:` images work |
| Text | `.txt`, `.text`, `.log` | Blank lines separate paragraphs; UTF-8 or Windows-1252 |
| CSV | `.csv` | Table with the first row as header |
| Images | `.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`, `.tif`, `.tiff` | One image on a page, scaled to fit |

Limitations: legacy `.doc`, `.odt`, `.pptx` and `.xlsx` are not supported. Word character
formatting (fonts, colours, bold/italic) is not carried over, and vector images (EMF/WMF)
inside Word files are skipped. Text uses the standard Helvetica font, so scripts outside
Western European languages may not render.

### HTTP API

| Endpoint | Description |
| --- | --- |
| `POST /api/convert` | Multipart form with a `file` field. Returns `application/pdf` with the Producer in the `X-PDF-Producer` header. Errors return `{"error": "..."}` with 400, 413, 415 (unsupported format) or 422 (unreadable file) |
| `GET /api/formats` | `{"extensions": [...]}` |

```sh
curl -F file=@report.docx http://localhost:8080/api/convert -o report.pdf
```

## Java API

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

To convert files from code, use the same service the web UI uses:

```java
byte[] pdf = DocumentConversionService.withDefaults()
        .convert(new SourceDocument("report.docx", Files.readAllBytes(path)));
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

Document options: `author`, `subject`, `keywords`, `pageNumbers(false)` to turn off the
"Page X of Y" footer, and `titleOnPage(false)` to keep the title in metadata only.
Use `new PdfGenerator(PageSize.LETTER)` for other page sizes.

### Run the example

```sh
mvn compile exec:java                                   # writes output/example.pdf
mvn compile exec:java -Dexec.args="out/report.pdf"      # custom path
```

## Tests

```sh
mvn test
```

The tests read the generated PDFs back with iText and check the Producer metadata and
rendered content for the generator, every converter, and the HTTP endpoints.

## License note

iText Core and pdfHTML are licensed under the AGPL. Using them in closed-source software,
or running this as a network service without publishing your source, requires a commercial
license from Apryse.
