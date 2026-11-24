package com.wedid.backend.sdm.repository;

import com.wedid.backend.sdm.service.SdmBasePrices;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Map;

/**
 * 스드메 업체별 대표 구성 공개 가격 행. 한 번의 쿼리로 세 카테고리를 모두 읽는다.
 * 금액(최저)이 없는 행은 기본가를 만들 수 없어 뺀다.
 */
@Repository
public class SdmBasePriceRepository {

    private static final String SQL = """
            SELECT v.id AS vendor_id, v.name AS vendor_name, v.category, v.source_agency_count, v.official_url,
                   a.source_name AS agency_name, pr.source_url, pr.file_reference_raw, pr.collected_at,
                   d.amount_min, d.amount_max
            FROM public.products p
            JOIN public.vendors v ON v.id = p.vendor_id
            JOIN public.price_records pr ON pr.product_id = p.id
            JOIN public.product_price_details d ON d.price_record_id = pr.id
            LEFT JOIN public.agencies a ON a.id = pr.agency_id
            WHERE p.product_kind = 'SDM_BASE'
              AND v.source_dataset = 'sdm_vendors'
              AND v.category = p.category
              AND d.amount_min IS NOT NULL
              AND ((p.category = 'STUDIO' AND p.product_type_standard = :studioType)
                OR (p.category = 'DRESS' AND p.product_type_standard = :dressType)
                OR (p.category = 'MAKEUP' AND p.product_type_standard = :makeupType))
            ORDER BY v.id, pr.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SdmBasePriceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SdmBasePriceRow> findRepresentativePriceRows() {
        Map<String, String> types = SdmBasePrices.REPRESENTATIVE_PRODUCT_TYPES;
        Map<String, Object> params = Map.of(
                "studioType", types.get("STUDIO"),
                "dressType", types.get("DRESS"),
                "makeupType", types.get("MAKEUP"));

        return jdbc.query(SQL, params, (rs, rowNum) -> {
            Date collectedAt = rs.getDate("collected_at");
            int agencyCount = rs.getInt("source_agency_count");
            Integer vendorAgencyCount = rs.wasNull() ? null : agencyCount;
            return new SdmBasePriceRow(
                    rs.getLong("vendor_id"),
                    rs.getString("vendor_name"),
                    rs.getString("category"),
                    vendorAgencyCount,
                    rs.getString("official_url"),
                    rs.getString("agency_name"),
                    rs.getString("source_url"),
                    rs.getString("file_reference_raw"),
                    collectedAt == null ? null : collectedAt.toLocalDate(),
                    rs.getBigDecimal("amount_min"),
                    rs.getBigDecimal("amount_max"));
        });
    }
}
