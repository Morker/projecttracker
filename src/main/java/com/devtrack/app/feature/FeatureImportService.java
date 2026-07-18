package com.devtrack.app.feature;

import com.opencsv.CSVReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class FeatureImportService {

    private static final Logger log = LoggerFactory.getLogger(FeatureImportService.class);

    public List<FeatureRow> parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new IllegalArgumentException("File has no name");
        String lower = filename.toLowerCase();

        if (lower.endsWith(".csv")) return parseCsv(file);
        if (lower.endsWith(".xlsx")) return parseXlsx(file);
        throw new IllegalArgumentException("Unsupported format: " + filename + ". Use .csv or .xlsx");
    }

    List<FeatureRow> parseCsv(MultipartFile file) {
        List<FeatureRow> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVReader reader = new CSVReader(br)) {

            String[] header = reader.readNext();
            if (header == null) return rows;

            int titleIdx = indexOf(header, "title", "name", "feature");
            int descIdx = indexOf(header, "description", "desc", "details");
            int statusIdx = indexOf(header, "status");

            String[] line;
            int lineNum = 1;
            while ((line = reader.readNext()) != null) {
                lineNum++;
                if (isEmpty(line)) continue;

                FeatureRow row = new FeatureRow();
                row.setTitle(titleIdx >= 0 && line.length > titleIdx ? safe(line[titleIdx]) : "");
                row.setDescription(descIdx >= 0 && line.length > descIdx ? safe(line[descIdx]) : null);
                row.setStatusString(statusIdx >= 0 && line.length > statusIdx ? safe(line[statusIdx]) : null);
                if (!row.getTitle().isEmpty()) rows.add(row);
                else log.warn("Skipping CSV row {}: empty title", lineNum);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV: " + e.getMessage(), e);
        }
        return rows;
    }

    List<FeatureRow> parseXlsx(MultipartFile file) {
        List<FeatureRow> rows = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook wb = new XSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) return rows;

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;

            int titleIdx = indexOf(headerRow, "title", "name", "feature");
            int descIdx = indexOf(headerRow, "description", "desc", "details");
            int statusIdx = indexOf(headerRow, "status");

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmpty(row)) continue;

                FeatureRow fr = new FeatureRow();
                fr.setTitle(titleIdx >= 0 ? cellString(row.getCell(titleIdx)) : "");
                fr.setDescription(descIdx >= 0 ? cellString(row.getCell(descIdx)) : null);
                fr.setStatusString(statusIdx >= 0 ? cellString(row.getCell(statusIdx)) : null);
                if (!fr.getTitle().isEmpty()) rows.add(fr);
                else log.warn("Skipping Excel row {}: empty title", i + 1);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XLSX: " + e.getMessage(), e);
        }
        return rows;
    }

    private String cellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield ""; }
            }
            default -> "";
        };
    }

    private int indexOf(String[] arr, String... candidates) {
        for (int i = 0; i < arr.length; i++)
            for (String c : candidates)
                if (arr[i].trim().equalsIgnoreCase(c)) return i;
        return -1;
    }

    private int indexOf(Row headerRow, String... candidates) {
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String val = cellString(cell);
            for (String c : candidates)
                if (val.equalsIgnoreCase(c)) return i;
        }
        return -1;
    }

    private boolean isEmpty(String[] line) {
        if (line == null) return true;
        for (String s : line) if (s != null && !s.trim().isEmpty()) return false;
        return true;
    }

    private boolean isEmpty(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c != null && !cellString(c).isEmpty()) return false;
        }
        return true;
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    public static class FeatureRow {
        private String title;
        private String description;
        private String statusString;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatusString() { return statusString; }
        public void setStatusString(String statusString) { this.statusString = statusString; }

        public FeatureStatus resolveStatus() {
            if (statusString == null || statusString.isBlank()) return FeatureStatus.PLANNED;
            String s = statusString.trim().toLowerCase();
            if (s.contains("progress")) return FeatureStatus.IN_PROGRESS;
            if (s.equals("done") || s.equals("complete")) return FeatureStatus.DONE;
            try { return FeatureStatus.valueOf(statusString.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return FeatureStatus.PLANNED; }
        }
    }
}
