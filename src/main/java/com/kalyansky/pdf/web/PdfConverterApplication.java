package com.kalyansky.pdf.web;

import com.kalyansky.pdf.convert.DocumentConversionService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Web app: upload a document in the browser and get it back as a PDF written by iText.
 */
@SpringBootApplication
public class PdfConverterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfConverterApplication.class, args);
    }

    @Bean
    DocumentConversionService documentConversionService() {
        return DocumentConversionService.withDefaults();
    }
}
