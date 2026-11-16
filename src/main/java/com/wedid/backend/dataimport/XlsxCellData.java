package com.wedid.backend.dataimport;

// 원본 엑셀 셀 하나의 정보를 보관
public record XlsxCellData(
        String address,          // 셀 주소: A2, B2 등
        int columnNumber,        // 열 번호: 1부터 시작
        String columnName,       // 해당 열의 헤더 원문
        String cellType,         // STRING, NUMERIC, BOOLEAN 등
        String rawValue,         // 원본 값의 문자열 표현
        String displayedValue,   // 엑셀 서식을 적용한 표시값
        String formula,          // 수식 셀의 수식, 없으면 null
        String dateTimeValue     // 날짜 셀의 해석값, 아니면 null
) {
}