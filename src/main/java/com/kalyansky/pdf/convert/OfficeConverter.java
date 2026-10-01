package com.kalyansky.pdf.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Word, Excel, PowerPoint and OpenDocument files. LibreOffice renders the document exactly as
 * laid out (fonts, colours, spacing, headers/footers, images, page setup), then iText writes
 * the final PDF from those pages, so the result looks like the original and is produced by iText.
 */
public class OfficeConverter implements DocumentConverter {

    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    private static final byte[] OLE_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final byte[] RTF_MAGIC = {'{', '\\', 'r', 't', 'f'};

    private final String soffice;
    // Each LibreOffice run takes a few hundred MB; cap how many run at once
    private final Semaphore slots;

    public OfficeConverter() {
        this(System.getenv().getOrDefault("SOFFICE_PATH", "soffice"),
                Integer.parseInt(System.getenv().getOrDefault("OFFICE_MAX_PARALLEL", "2")));
    }

    public OfficeConverter(String soffice, int maxParallel) {
        this.soffice = soffice;
        this.slots = new Semaphore(maxParallel, true);
    }

    @Override
    public Set<String> extensions() {
        return Set.of("docx", "doc", "odt", "rtf", "xlsx", "xls", "ods", "pptx", "ppt", "odp");
    }

    @Override
    public byte[] convert(SourceDocument source) throws IOException {
        checkSignature(source);
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                throw ConversionException.unavailable("The server is busy. Try again in a moment.", null);
            }
            return ITextRewriter.rewrite(renderWithLibreOffice(source), source.baseName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ConversionException.unavailable("Conversion was interrupted", e);
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private byte[] renderWithLibreOffice(SourceDocument source) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("office-convert-");
        try {
            // A fixed input name keeps the user's file name out of the command line
            Path input = workDir.resolve("input." + source.extension());
            Path outDir = workDir.resolve("out");
            Files.write(input, source.data());

            List<String> command = List.of(
                    soffice,
                    // A private profile per run lets conversions run in parallel
                    "-env:UserInstallation=" + workDir.resolve("profile").toUri(),
                    "--headless", "--norestore", "--nolockcheck", "--nodefault", "--nologo",
                    "--convert-to", "pdf",
                    "--outdir", outDir.toString(),
                    input.toString());

            Process process;
            try {
                process = new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .redirectOutput(workDir.resolve("soffice.log").toFile())
                        .start();
            } catch (IOException e) {
                throw ConversionException.unavailable(
                        "Office documents cannot be converted: LibreOffice is not installed on the server", e);
            }

            if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw ConversionException.unreadable(
                        "Converting " + source.filename() + " took too long", null);
            }

            Path pdf = outDir.resolve("input.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf) || Files.size(pdf) == 0) {
                String log = Files.readString(workDir.resolve("soffice.log")).strip();
                throw ConversionException.unreadable("Could not read " + source.filename()
                        + ". Is it a valid ." + source.extension() + " file?",
                        new IOException("soffice exit " + process.exitValue() + ": " + log));
            }
            return Files.readAllBytes(pdf);
        } finally {
            deleteRecursively(workDir);
        }
    }

    /**
     * LibreOffice opens files it cannot parse as plain text, which would turn a corrupt or
     * mislabelled upload into a page of garbage, so the file's header must match its extension.
     */
    private static void checkSignature(SourceDocument source) {
        byte[] expected = switch (source.extension()) {
            case "doc", "xls", "ppt" -> OLE_MAGIC;
            case "rtf" -> RTF_MAGIC;
            default -> ZIP_MAGIC; // OOXML (docx/xlsx/pptx) and OpenDocument are ZIP packages
        };
        byte[] data = source.data();
        boolean matches = data.length >= expected.length;
        for (int i = 0; matches && i < expected.length; i++) {
            matches = data[i] == expected[i];
        }
        if (!matches) {
            throw ConversionException.unreadable("Could not read " + source.filename()
                    + ". Is it a valid ." + source.extension() + " file?", null);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // Temp files are cleaned up by the OS eventually
        }
    }
}
