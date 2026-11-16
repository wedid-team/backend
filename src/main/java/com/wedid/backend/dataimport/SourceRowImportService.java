package com.wedid.backend.dataimport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

@Service
public class SourceRowImportService {

    private final SourceRowRepository sourceRowRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public SourceRowImportService(
            SourceRowRepository sourceRowRepository
    ) {
        this.sourceRowRepository = sourceRowRepository;
    }

    // 파일 전체 저장 중 오류가 발생하면 이번 원본 행 저장을 취소
    @Transactional(rollbackFor = Exception.class)
    public int importWorkbook(
            Long sourceFileId,
            Path filePath,
            Set<String> dataSheetNames
    ) throws IOException {

        XlsxReader reader = new XlsxReader();
        int processedRows = 0;

        for (String sheetName : reader.readSheetNames(filePath)) {
            var rows = reader.readAllRows(filePath, sheetName);

            for (var entry : rows.entrySet()) {
                int rowNumber = entry.getKey();
                var cells = entry.getValue();

                boolean hasContent = cells.stream().anyMatch(cell ->
                        cell.formula() != null
                                || (cell.rawValue() != null
                                && !cell.rawValue().isBlank())
                );

                boolean needsReview = cells.stream().anyMatch(cell ->
                        "FORMULA".equals(cell.cellType())
                                || "ERROR".equals(cell.cellType())
                );

                String parseStatus;

                if (rowNumber == 1
                        || !dataSheetNames.contains(sheetName)
                        || !hasContent) {
                    parseStatus = "REFERENCE";
                } else if (needsReview) {
                    parseStatus = "REVIEW";
                } else {
                    parseStatus = "READY";
                }

                String cellsJson = jsonMapper.writeValueAsString(cells);

                sourceRowRepository.register(
                        sourceFileId,
                        sheetName,
                        rowNumber,
                        cellsJson,
                        parseStatus
                );

                processedRows++;
            }
        }

        // 새로 추가된 수가 아니라 확인·처리한 원본 행 수
        return processedRows;
    }
}