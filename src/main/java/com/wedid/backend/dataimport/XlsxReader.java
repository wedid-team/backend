package com.wedid.backend.dataimport;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;

import java.math.BigDecimal;

import org.apache.poi.xssf.usermodel.XSSFCell;

import java.util.LinkedHashMap;
import java.util.Map;

// 원본 엑셀 파일을 읽는 클래스
public class XlsxReader {

    // 엑셀 파일 안에 있는 시트 이름 목록을 반환
    public List<String> readSheetNames(Path filePath) throws IOException {
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("엑셀 파일을 찾을 수 없습니다: " + filePath);
        }

        List<String> sheetNames = new ArrayList<>();

        // true: 원본 파일을 읽기 전용으로 열기
        // try 블록이 끝나면 파일을 자동으로 닫음
        try (Workbook workbook =
                     WorkbookFactory.create(filePath.toFile(), null, true)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }
        }

        return sheetNames;
    }
    // 지정한 시트의 첫 행에서 컬럼 이름을 읽음
    public List<String> readHeaders(Path filePath, String sheetName)
            throws IOException {

        if (!Files.isRegularFile(filePath)) {
            throw new IOException("엑셀 파일을 찾을 수 없습니다: " + filePath);
        }

        try (Workbook workbook =
                     WorkbookFactory.create(filePath.toFile(), null, true)) {

            var sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                throw new IOException("시트를 찾을 수 없습니다: " + sheetName);
            }

            Row headerRow = sheet.getRow(0);

            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                throw new IOException("첫 행이 비어 있습니다: " + sheetName);
            }

            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();

            // 빈 셀도 포함해서 원래 컬럼 순서를 유지
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers.add(formatter.formatCellValue(headerRow.getCell(i)));
            }

            return headers;
        }
    }
    // 첫 번째 데이터 행을 읽음: 1행은 헤더, 2행은 데이터
    public List<Object> readFirstDataRow(Path filePath, String sheetName)
            throws IOException {

        if (!Files.isRegularFile(filePath)) {
            throw new IOException("엑셀 파일을 찾을 수 없습니다: " + filePath);
        }

        try (Workbook workbook =
                     WorkbookFactory.create(filePath.toFile(), null, true)) {

            var sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                throw new IOException("시트를 찾을 수 없습니다: " + sheetName);
            }

            Row headerRow = sheet.getRow(0);
            Row dataRow = sheet.getRow(1);

            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                throw new IOException("헤더가 비어 있습니다: " + sheetName);
            }

            if (dataRow == null) {
                throw new IOException("첫 번째 데이터 행이 없습니다: " + sheetName);
            }

            List<Object> values = new ArrayList<>();

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = dataRow.getCell(
                        i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );

                // 빈칸은 0이나 빈 문자열로 바꾸지 않고 null로 유지
                if (cell == null) {
                    values.add(null);
                    continue;
                }

                Object value = switch (cell.getCellType()) {
                    case STRING -> cell.getStringCellValue();
                    case BOOLEAN -> cell.getBooleanCellValue();
                    case NUMERIC -> {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            yield cell.getLocalDateTimeCellValue();
                        }
                        yield BigDecimal.valueOf(cell.getNumericCellValue());
                    }
                    case BLANK -> null;

                    // 수식이나 오류 셀을 임의의 값으로 변환하지 않음
                    default -> throw new IOException(
                            "별도 처리가 필요한 셀: "
                                    + cell.getAddress()
                                    + " / 유형: " + cell.getCellType()
                    );
                };

                values.add(value);
            }

            return values;
        }
    }
    // 헤더를 포함한 시트의 모든 실제 행을 읽음
// 반환값: 엑셀 행 번호 → 해당 행의 셀 목록
    public Map<Integer, List<XlsxCellData>> readAllRows(
            Path filePath,
            String sheetName
    ) throws IOException {

        if (!Files.isRegularFile(filePath)) {
            throw new IOException("엑셀 파일을 찾을 수 없습니다: " + filePath);
        }

        try (Workbook workbook =
                     WorkbookFactory.create(filePath.toFile(), null, true)) {

            var sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                throw new IOException("시트를 찾을 수 없습니다: " + sheetName);
            }

            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(0);

            int headerWidth = headerRow == null
                    ? 0
                    : Math.max(0, headerRow.getLastCellNum());

            Map<Integer, List<XlsxCellData>> rows = new LinkedHashMap<>();

            for (Row row : sheet) {
                int width = Math.max(
                        headerWidth,
                        Math.max(0, row.getLastCellNum())
                );

                List<XlsxCellData> cells = new ArrayList<>();

                for (int column = 0; column < width; column++) {
                    Cell cell = row.getCell(column);

                    String address =
                            new org.apache.poi.ss.util.CellAddress(
                                    row.getRowNum(), column
                            ).formatAsString();

                    String columnName = headerRow == null
                            ? ""
                            : formatter.formatCellValue(
                            headerRow.getCell(column)
                    );

                    String cellType = cell == null
                            ? "MISSING"
                            : cell.getCellType().name();

                    String rawValue = null;
                    String displayedValue = "";
                    String formula = null;
                    String dateTimeValue = null;

                    if (cell != null) {
                        displayedValue = formatter.formatCellValue(cell);

                        switch (cell.getCellType()) {
                            case STRING ->
                                    rawValue = cell.getStringCellValue();

                            case BOOLEAN ->
                                    rawValue = Boolean.toString(
                                            cell.getBooleanCellValue()
                                    );

                            case NUMERIC -> {
                                rawValue = ((XSSFCell) cell).getRawValue();

                                if (DateUtil.isCellDateFormatted(cell)) {
                                    dateTimeValue =
                                            cell.getLocalDateTimeCellValue()
                                                    .toString();
                                }
                            }

                            case FORMULA -> {
                                formula = cell.getCellFormula();
                                rawValue = ((XSSFCell) cell).getRawValue();

                                // 数式は再計算せず、保存済みの値を原本として保持
                                displayedValue = null;
                            }

                            case ERROR ->
                                    rawValue = ((XSSFCell) cell).getRawValue();

                            case BLANK, _NONE -> {
                                // 空欄は null のまま保持
                            }
                        }
                    }

                    cells.add(new XlsxCellData(
                            address,
                            column + 1,
                            columnName,
                            cellType,
                            rawValue,
                            displayedValue,
                            formula,
                            dateTimeValue
                    ));
                }

                rows.put(row.getRowNum() + 1, cells);
            }

            return rows;
        }
    }
}