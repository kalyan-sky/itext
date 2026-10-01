# itext

PDF generation built on [iText](https://itextpdf.com/) (Core 9.8.0). Every PDF this
project writes, whether it comes from code or from an uploaded document, is produced by
iText, so its **Producer** metadata reads `iText® Core 9.8.0 ...`.

- **Web UI**: upload a document in the browser and get it back as a PDF that looks the same
  as the original.
- **Java API**: describe a document in code with `PdfContent` and render it with `PdfGenerator`.

## Requirements

- Java 21+
- Maven 3.9+
- [LibreOffice](https://www.libreoffice.org/) (`soffice` on the `PATH`, or set `SOFFICE_PATH`)
  for Word, Excel, PowerPoint and OpenDocument files. The Docker image includes it. On
  Ubuntu: `apt-get install libreoffice-writer-nogui libreoffice-calc-nogui libreoffice-impress-nogui fonts-liberation fonts-crosextra-carlito fonts-crosextra-caladea`

## Web UI: convert documents to PDF

```sh
mvn spring-boot:run
# or
mvn package && java -jar target/itext-pdf-generator-1.0.0-SNAPSHOT.jar
```

Open http://localhost:8080, drop a file on the page (or browse for one) and click
**Convert to PDF**. The page previews the PDF, shows its Producer, and lets you download it.
Set `PORT` to use a different port. Uploads are limited to 20 MB.

Uploaded documents are converted as they are: the PDF keeps the original's layout and
formatting rather than being restyled.

| Format | Extensions | How it is converted |
| --- | --- | --- |
| Word, Excel, PowerPoint, OpenDocument, RTF | `.docx`, `.doc`, `.odt`, `.rtf`, `.xlsx`, `.xls`, `.ods`, `.pptx`, `.ppt`, `.odp` | LibreOffice renders the document with its own layout: fonts, sizes, colours, bold/italic/underline, highlighting, alignment, tables and their styles, lists, images, headers and footers, page size and orientation. iText then copies those pages unchanged into a new PDF, so the file is produced by iText |
| HTML | `.html`, `.htm` | Rendered with iText pdfHTML using the page's own CSS. External files and URLs are not loaded; inline `data:` images work |
| Text | `.txt`, `.text`, `.log` | Exactly as in the file: every line, space, tab and blank line, in a monospace font. UTF-8 or Windows-1252 |
| CSV | `.csv` | Table with the first row as header |
| Images | `.png`, `.jpg`, `.jpeg`, `.gif`, `.bmp`, `.tif`, `.tiff` | One image on a page, scaled down only if larger than the page |

Nothing is added to converted documents: no title line and no page numbers.

Fonts: a document can only be shown in fonts the server has. The Docker image ships
Liberation (same metrics as Arial, Times New Roman and Courier New), Carlito and Caladea
(same metrics as Calibri and Cambria), DejaVu and Noto, so line and page breaks match the
original. Other fonts, such as Georgia or Segoe UI, are replaced by a similar font. Install
additional fonts in the image if your documents need them.

Limits: 20 MB per file, 90 seconds per Office conversion, two Office conversions at a time
per instance (`OFFICE_MAX_PARALLEL`). Office files are checked against their format's file
signature, so a renamed or corrupt file is rejected rather than printed as raw text.

### HTTP API

| Endpoint | Description |
| --- | --- |
| `POST /api/convert` | Multipart form with a `file` field. Returns `application/pdf` with the Producer in the `X-PDF-Producer` header. Errors return `{"error": "..."}` with 400, 413, 415 (unsupported format), 422 (unreadable file) or 503 (LibreOffice missing or busy) |
| `GET /api/formats` | `{"extensions": [...]}` |

```sh
curl -F file=@report.docx http://localhost:8080/api/convert -o report.pdf
```

## Deploy to Google Cloud Run

The `Dockerfile` builds the app and runs it on a Java 21 JRE with LibreOffice and fonts, as a
non-root user. Cloud Run passes the port in `PORT`, which the app reads. Give the service
2 GiB of memory: each Office conversion runs a LibreOffice process.

### From your machine

Requires the [gcloud CLI](https://cloud.google.com/sdk/docs/install) and a project with
billing enabled.

```sh
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

gcloud run deploy itext-pdf-converter \
  --source . \
  --region us-central1 \
  --allow-unauthenticated \
  --memory 2Gi --cpu 1 --concurrency 20 --timeout 120 --max-instances 3
```

`--source .` uploads the repo, builds the Dockerfile with Cloud Build, and deploys it. The
command prints the service URL when it finishes. Drop `--allow-unauthenticated` to keep the
service private (callers then need an identity token). `--max-instances` caps cost.

### From GitHub Actions

`.github/workflows/deploy-cloud-run.yml` runs the tests and deploys on every push to `main`
(or manually from the Actions tab). One-time setup:

1. Create a deployer service account and give it the roles `gcloud run deploy --source` needs:

   ```sh
   PROJECT=YOUR_PROJECT_ID
   gcloud iam service-accounts create github-deployer --project $PROJECT
   SA=github-deployer@$PROJECT.iam.gserviceaccount.com
   for role in roles/run.admin roles/cloudbuild.builds.editor roles/artifactregistry.admin \
               roles/storage.admin roles/iam.serviceAccountUser roles/serviceusage.serviceUsageConsumer; do
     gcloud projects add-iam-policy-binding $PROJECT --member serviceAccount:$SA --role $role
   done
   gcloud iam service-accounts keys create key.json --iam-account $SA
   ```

2. In the GitHub repo, under **Settings → Secrets and variables → Actions**:
   - secret `GCP_SA_KEY`: the contents of `key.json` (then delete the local file)
   - variable `GCP_PROJECT_ID`: your project id
   - variable `GCP_REGION` (optional): defaults to `us-central1`

The deploy job is skipped until `GCP_PROJECT_ID` is set.

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
rendered content for the generator, every converter, and the HTTP endpoints. The Word test
checks that page size, orientation, fonts, colours and text sizes survive conversion; it is
skipped when LibreOffice is not installed.

## License note

iText Core and pdfHTML are licensed under the AGPL. Using them in closed-source software,
or running this as a network service without publishing your source, requires a commercial
license from Apryse.
