package com.kalyansky.pdf.web;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.kalyansky.pdf.convert.DocumentConversionService;
import com.kalyansky.pdf.convert.SourceDocument;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ConversionController {

    /** Response header carrying the Producer entry of the generated PDF. */
    public static final String PRODUCER_HEADER = "X-PDF-Producer";

    private final DocumentConversionService conversionService;

    public ConversionController(DocumentConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @GetMapping("/formats")
    public Map<String, Set<String>> formats() {
        return Map.of("extensions", conversionService.supportedExtensions());
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convert(@RequestParam("file") MultipartFile file) throws IOException {
        SourceDocument source = new SourceDocument(file.getOriginalFilename(), file.getBytes());
        byte[] pdf = conversionService.convert(source);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(source.baseName() + ".pdf", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header(PRODUCER_HEADER, readProducer(pdf))
                .body(pdf);
    }

    private static String readProducer(byte[] pdf) throws IOException {
        try (PdfDocument document = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdf)))) {
            String producer = document.getDocumentInfo().getProducer();
            // HTTP header values must be ASCII; iText's producer contains "®"
            return producer == null ? "" : producer
                    .replace("\u00AE", "")
                    .replace("\u00A9", "(c)")
                    .replaceAll("[^\\x20-\\x7E]", "");
        }
    }
}
