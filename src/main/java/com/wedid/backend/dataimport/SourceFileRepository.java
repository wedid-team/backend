package com.wedid.backend.dataimport;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SourceFileRepository {

    private final JdbcTemplate jdbcTemplate;

    public SourceFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 파일 정보를 등록하고 DB의 파일 ID를 반환
    @Transactional
    public Long register(
            String dataset,
            String originalFilename,
            String sha256
    ) {
        String insertSql = """
                INSERT INTO public.source_files (
                    dataset,
                    original_filename,
                    sha256
                )
                VALUES (?, ?, ?)
                ON CONFLICT (dataset, sha256) DO NOTHING
                """;

        jdbcTemplate.update(
                insertSql,
                dataset,
                originalFilename,
                sha256
        );

        String selectSql = """
                SELECT id
                FROM public.source_files
                WHERE dataset = ?
                  AND sha256 = ?
                """;

        return jdbcTemplate.queryForObject(
                selectSql,
                Long.class,
                dataset,
                sha256
        );
    }
}