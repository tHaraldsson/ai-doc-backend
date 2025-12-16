package com.haraldsson.aidocbackend.filemanagement.service;

import com.haraldsson.aidocbackend.advice.exceptions.FileProcessingException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Service
public class ExcelProcessorService {

    private static final Logger log = LoggerFactory.getLogger(ExcelProcessorService.class);

    public Mono<String> extractTextFromExcel(byte[] excelBytes) {
        return Mono.fromCallable(() -> {
            if (excelBytes == null || excelBytes.length == 0) {
                throw new FileProcessingException("Excel file is empty");
            }

            try (Workbook workbook = createWorkbook(excelBytes)) {
                StringBuilder result = new StringBuilder();

                result.append("=== EXCEL DOCUMENT ===\n\n");

                for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                    Sheet sheet = workbook.getSheetAt(sheetIndex);
                    String sheetName = sheet.getSheetName();

                    result.append("--- WORKSHEET: '").append(sheetName).append("' ---\n\n");

                    List<List<String>> tableData = readSheetAsTable(sheet);

                    if (tableData.isEmpty()) {
                        result.append("(empty sheet)\n\n");
                        continue;
                    }

                    result.append("DATA IN TABULAR FORM:\n");
                    for (List<String> row : tableData) {
                        result.append(String.join(" | ", row)).append("\n");
                    }
                    result.append("\n");

                    result.append("SUMMARY: ")
                            .append(tableData.size() - 1).append(" data rows, ")
                            .append(tableData.get(0).size()).append(" columns\n\n");
                }

                return result.toString();
            } catch (Exception e) {
                log.error("Excel processing error: {}", e.getMessage());
                throw new FileProcessingException("Excel processing error: " + e.getMessage(), e);
            }
        });
    }

    private Workbook createWorkbook(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 8) {
            throw new FileProcessingException("Invalid Excel file - file is too small or corrupted");
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            // Försöker först med XLSX sedan med XLS
            if (isXlsxFile(bytes)) {
                try {
                    return new XSSFWorkbook(bis);
                } catch (Exception e) {
                    log.warn("XSSFWorkbook failed, trying HSSFWorkbook: {}", e.getMessage());
                    bis.reset();
                    return new HSSFWorkbook(bis);
                }
            } else {
                return new HSSFWorkbook(bis);
            }
        } catch (Exception e) {
            throw new FileProcessingException("Cannot read Excel file. File might be corrupted or in wrong format", e);
        }
    }

    private boolean isXlsxFile(byte[] bytes) {
        // XLSX-filer börjar med PK ZIP-header
        return bytes.length > 4 && bytes[0] == 0x50 && bytes[1] == 0x4B;
    }

    private List<List<String>> readSheetAsTable(Sheet sheet) {
        List<List<String>> table = new ArrayList<>();
        int maxColumns = findMaxColumns(sheet);

        // headers /kolumnnummer
        if (maxColumns > 0) {
            List<String> headerRow = new ArrayList<>();
            for (int col = 0; col < maxColumns; col++) {
                headerRow.add("Col " + (col + 1));
            }
            table.add(headerRow);
        }

        for (Row row : sheet) {
            List<String> rowData = new ArrayList<>();

            for (int col = 0; col < maxColumns; col++) {
                Cell cell = row.getCell(col, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                rowData.add(getCellValueAsString(cell));
            }

            if (!isRowEmpty(rowData)) {
                table.add(rowData);
            }
        }

        return table;
    }

    private int findMaxColumns(Sheet sheet) {
        int maxCols = 0;
        for (Row row : sheet) {
            int lastCellNum = row.getLastCellNum();
            if (lastCellNum > maxCols) {
                maxCols = lastCellNum;
            }
        }
        return Math.min(maxCols, 50); // max 50 kolumner
    }

    private boolean isRowEmpty(List<String> row) {
        return row.stream().allMatch(cell -> cell == null || cell.trim().isEmpty());
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getDateCellValue().toString();
                    } else {
                        double value = cell.getNumericCellValue();
                        if (value == Math.floor(value) && !Double.isInfinite(value)) {
                            yield String.valueOf((long) value);
                        } else {
                            yield String.valueOf(value);
                        }
                    }
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try {
                        yield String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e) {
                        try {
                            yield cell.getStringCellValue();
                        } catch (Exception e2) {
                            yield cell.getCellFormula();
                        }
                    }
                }
                default -> "";
            };
        } catch (Exception e) {
            log.warn("Error reading cell value: {}", e.getMessage());
            return "[ERROR]";
        }
    }
}