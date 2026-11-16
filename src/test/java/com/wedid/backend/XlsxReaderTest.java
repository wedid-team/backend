package com.wedid.backend;

import com.wedid.backend.dataimport.XlsxReader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import com.wedid.backend.dataimport.XlsxCellData;

class XlsxReaderTest {

    @Test
    void readWeddingVenueSheetNames() throws IOException {
        // 현재 사용자 계정의 Downloads 폴더에 있는 원본 파일
        Path filePath = Path.of(
                System.getProperty("user.home"),
                "Downloads",
                "wedding_hall_prices.xlsx"
        );

        XlsxReader reader = new XlsxReader();

        // 읽기 전용으로 시트 이름 확인
        List<String> sheetNames = reader.readSheetNames(filePath);

        assertFalse(sheetNames.isEmpty(), "엑셀에 시트가 있어야 합니다.");

        System.out.println("읽은 파일: " + filePath);
        System.out.println("시트 목록: " + sheetNames);
        List<String> headers = reader.readHeaders(filePath, "홀별 가격");

        assertFalse(headers.isEmpty(), "컬럼 이름이 있어야 합니다.");

        for (int i = 0; i < headers.size(); i++) {
            System.out.println("컬럼 " + (i + 1) + ": " + headers.get(i));
        }
        List<Object> values =
                reader.readFirstDataRow(filePath, "홀별 가격");

        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);

            String type = value == null
                    ? "빈칸"
                    : value.getClass().getSimpleName();

            System.out.println(
                    headers.get(i) + " = " + value + " [" + type + "]"
            );
        }
        // 참고 시트까지 포함해서 전체 행 읽기 확인
        for (String sheetName : sheetNames) {
            var rows = reader.readAllRows(filePath, sheetName);

            // 헤더를 제외하고 실제 내용이 있는 행 수 확인
            long contentRowCount = rows.entrySet().stream()
                    .filter(entry -> entry.getKey() > 1)
                    .filter(entry -> entry.getValue().stream().anyMatch(cell ->
                            cell.formula() != null
                                    || (cell.rawValue() != null
                                    && !cell.rawValue().isBlank())
                    ))
                    .count();

            System.out.println(
                    "내용 있는 행 수: " + sheetName
                            + " / 헤더 제외=" + contentRowCount
            );

            assertFalse(rows.isEmpty(), "시트가 비어 있습니다: " + sheetName);

            System.out.println(
                    "전체 행 읽기: " + sheetName
                            + " / 헤더 포함 행 수=" + rows.size()
            );

            // 엑셀의 2행이 있으면 셀 정보 출력
            var secondRow = rows.get(2);

            if (secondRow != null) {
                for (XlsxCellData cell : secondRow) {
                    System.out.println(
                            cell.address()
                                    + " / 컬럼=" + cell.columnName()
                                    + " / 유형=" + cell.cellType()
                                    + " / 원본=" + cell.rawValue()
                                    + " / 날짜=" + cell.dateTimeValue()
                    );
                }
            }
        }
    }

}