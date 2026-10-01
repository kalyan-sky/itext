package com.kalyansky.pdf.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConversionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void convertsUploadedFileToPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain",
                "Hello PDF".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/convert").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("notes.pdf")))
                .andExpect(header().string(ConversionController.PRODUCER_HEADER, containsString("iText")))
                .andExpect(result -> {
                    byte[] body = result.getResponse().getContentAsByteArray();
                    if (!new String(body, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                        throw new AssertionError("Response is not a PDF");
                    }
                });
    }

    @Test
    void rejectsUnsupportedFormatWithMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "song.mp3", "audio/mpeg", new byte[] {1, 2});

        mockMvc.perform(multipart("/api/convert").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error", containsString(".mp3")));
    }

    @Test
    void reportsMissingFile() throws Exception {
        mockMvc.perform(multipart("/api/convert"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void listsSupportedFormats() throws Exception {
        mockMvc.perform(get("/api/formats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extensions", hasItems("docx", "html", "txt", "csv", "png")));
    }

    @Test
    void servesTheUploadPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Document to PDF")));
    }
}
