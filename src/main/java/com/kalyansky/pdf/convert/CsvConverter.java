package com.kalyansky.pdf.convert;

import com.kalyansky.pdf.PdfGenerator;
import com.kalyansky.pdf.model.PdfContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * CSV: the first row becomes the table header. Short rows are padded with empty cells.
 */
public class CsvConverter implements DocumentConverter {

    private final PdfGenerator generator;

    public CsvConverter(PdfGenerator generator) {
        this.generator = generator;
    }

    @Override
    public Set<String> extensions() {
        return Set.of("csv");
    }

    @Override
    public byte[] convert(SourceDocument source) {
        List<List<String>> rows = parse(TextDecoding.decode(source.data()));
        if (rows.isEmpty()) {
            throw ConversionException.unreadable("The CSV file has no rows", null);
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        List<List<String>> padded = rows.stream().map(row -> pad(row, columns)).toList();

        PdfContent content = PdfContent.builder(source.baseName())
                .table(padded.get(0), padded.subList(1, padded.size()))
                .build();
        return generator.generate(content);
    }

    /** RFC 4180 parsing: quoted fields may contain commas, quotes ("") and line breaks. */
    static List<List<String>> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        row.add(field.toString());
        addRow(rows, row);
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean blank = row.stream().allMatch(String::isBlank);
        if (!blank) {
            rows.add(row);
        }
    }

    private static List<String> pad(List<String> row, int columns) {
        if (row.size() == columns) {
            return row;
        }
        List<String> copy = new ArrayList<>(row);
        copy.addAll(Collections.nCopies(columns - row.size(), ""));
        return copy;
    }
}
