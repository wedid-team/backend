package com.wedid.backend.dataimport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;

import java.util.Set;

@Component
@Profile("import-file-metadata")
public class SourceFileRegistrationRunner implements CommandLineRunner {

    private final SourceFileRepository sourceFileRepository;
    private final Path importDirectory;
    private final SourceRowImportService sourceRowImportService;

    public SourceFileRegistrationRunner(
            SourceFileRepository sourceFileRepository,
            SourceRowImportService sourceRowImportService,
            @Value("${wedid.import.directory}") String importDirectory
    ) {
        this.sourceFileRepository = sourceFileRepository;
        this.sourceRowImportService = sourceRowImportService;
        this.importDirectory = Path.of(importDirectory);
    }


    @Override
    @NullMarked
    public void run(String... args) throws Exception {
        String dataset = "wedding_venues";
        String filename = "wedding_venues.xlsx";

        Path filePath = importDirectory.resolve(filename);

        // 원본 파일을 읽어서 해시 계산
        FileHashCalculator calculator = new FileHashCalculator();
        String sha256 = calculator.calculate(filePath);

        // 파일 정보를 DB에 등록하거나 기존 등록 ID 조회
        Long sourceFileId = sourceFileRepository.register(
                dataset,
                filename,
                sha256
        );

        System.out.println(
                "파일 등록 확인: " + filename
                        + " / source_file_id=" + sourceFileId
        );
        // 모든 시트의 원본 행 저장
        // "예식장 목록"은 데이터 시트, 나머지는 참고 시트로 처리
        int processedRows = sourceRowImportService.importWorkbook(
                sourceFileId,
                filePath,
                Set.of("예식장 목록")
        );

        System.out.println(
                "원본 행 저장 완료: " + filename
                        + " / 처리한 행 수=" + processedRows
        );
        String priceDataset = "wedding_hall_prices";
        String priceFilename = "wedding_hall_prices.xlsx";

        Path priceFilePath = importDirectory.resolve(priceFilename);

        String priceSha256 = calculator.calculate(priceFilePath);

        Long priceSourceFileId = sourceFileRepository.register(
                priceDataset,
                priceFilename,
                priceSha256
        );

        System.out.println(
                "파일 등록 확인: " + priceFilename
                        + " / source_file_id=" + priceSourceFileId
        );

        int priceProcessedRows = sourceRowImportService.importWorkbook(
                priceSourceFileId,
                priceFilePath,
                Set.of("홀별 가격", "비용 항목")
        );

        System.out.println(
                "원본 행 저장 완료: " + priceFilename
                        + " / 처리한 행 수=" + priceProcessedRows
        );
        // 나머지 7개 XLSX 원본 적재
        String[][] remainingFiles = {
                {"wedding_options", "선택 품목"},
                {"sdm_vendors", "스드메 업체"},
                {"sdm_prices", "스드메 가격"},
                {"sdm_penalties", "스드메 위약금"},
                {"sdm_packages", "패키지"},
                {"sdm_options", "스드메 선택 품목"},
                {"market_stats", "참가격 통계"}
        };

        for (String[] file : remainingFiles) {
            String remainingDataset = file[0];
            String remainingFilename = remainingDataset + ".xlsx";
            String dataSheetName = file[1];

            Path remainingPath = importDirectory.resolve(remainingFilename);

            String remainingSha256 = calculator.calculate(remainingPath);

            Long remainingSourceFileId = sourceFileRepository.register(
                    remainingDataset,
                    remainingFilename,
                    remainingSha256
            );

            System.out.println(
                    "파일 등록 확인: " + remainingFilename
                            + " / source_file_id=" + remainingSourceFileId
            );

            int remainingProcessedRows = sourceRowImportService.importWorkbook(
                    remainingSourceFileId,
                    remainingPath,
                    Set.of(dataSheetName)
            );

            System.out.println(
                    "원본 행 저장 완료: " + remainingFilename
                            + " / 처리한 행 수=" + remainingProcessedRows
            );
        }
    }
}