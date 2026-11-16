package com.wedid.backend.dataimport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceRowRepository {

    private final JdbcTemplate jdbcTemplate;

    public SourceRowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 원본 행을 등록하고 DB의 행 ID를 반환
    @Transactional
    public Long register(
            Long sourceFileId,
            String sheetName,
            int excelRowNumber,
            String cellsJson,
            String parseStatus
    ) {
        String insertSql = """
                INSERT INTO public.source_rows (
                    source_file_id,
                    sheet_name,
                    excel_row_number,
                    cells,
                    parse_status
                )
                VALUES (?, ?, ?, CAST(? AS JSONB), ?)
                ON CONFLICT (
                    source_file_id,
                    sheet_name,
                    excel_row_number
                ) DO NOTHING
                """;

        jdbcTemplate.update(
                insertSql,
                sourceFileId,
                sheetName,
                excelRowNumber,
                cellsJson,
                parseStatus
        );

        String selectSql = """
                SELECT id
                FROM public.source_rows
                WHERE source_file_id = ?
                  AND sheet_name = ?
                  AND excel_row_number = ?
                """;

        return jdbcTemplate.queryForObject(
                selectSql,
                Long.class,
                sourceFileId,
                sheetName,
                excelRowNumber
        );
    }
}