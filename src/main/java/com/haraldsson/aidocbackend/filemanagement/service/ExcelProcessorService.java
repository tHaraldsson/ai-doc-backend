package com.haraldsson.aidocbackend.filemanagement.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@Service
public class ExcelProcessorService {

    public Mono<String> extractTextFromExcel(byte[] excelBytes) {
        return Mono.fromCallable(() -> {
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

                    result.append("CONCLUSION: ")
                            .append(tableData.size() - 1).append(" datarows, ")
                            .append(tableData.get(0).size()).append(" kolumns\n\n");
                }

                return result.toString();
            } catch (Exception e) {
                throw new RuntimeException("Excel processing error: " + e.getMessage(), e);
            }
        });
    }

    private Workbook createWorkbook(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            if (isXlsxFile(bytes)) {
                return new XSSFWorkbook(bis);
            } else {
                return new HSSFWorkbook(bis);
            }
        }
    }

    private boolean isXlsxFile(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == 0x50 && bytes[1] == 0x4B;
    }

    private List<List<String>> readSheetAsTable(Sheet sheet) {
        List<List<String>> table = new ArrayList<>();
        int maxColumns = findMaxColumns(sheet);

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
            maxCols = Math.max(maxCols, row.getLastCellNum());
        }
        return maxCols;
    }

    private boolean isRowEmpty(List<String> row) {
        return row.stream().allMatch(cell -> cell == null || cell.trim().isEmpty());
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
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
    }
}