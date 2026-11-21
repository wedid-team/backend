
package com.wedid.backend.recommendation.repository;

import com.wedid.backend.vendor.entity.HallPriceDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecommendationRepository
        extends JpaRepository<HallPriceDetail, Long> {

    // 1. 추천 가능한 예식장 가격 정보 조회
    @Query(value = """
            SELECT hpd.*
            FROM hall_price_details hpd
            JOIN price_records pr
                ON pr.id = hpd.price_record_id
            JOIN halls h
                ON h.id = pr.hall_id
            JOIN vendors v
                ON v.id = h.vendor_id
            JOIN regions r
                ON r.id = v.region_id
            WHERE v.category = 'WEDDING_HALL'
              AND v.operating_status_raw = U&'\\C6B4\\C601'
              AND r.code = :regionCode
              AND hpd.capacity_max >= :guestCount
              AND hpd.guaranteed_guests IS NOT NULL
              AND hpd.rental_min IS NOT NULL
              AND hpd.rental_max IS NOT NULL
              AND hpd.meal_min IS NOT NULL
              AND hpd.meal_max IS NOT NULL
              AND pr.vat_status IN ('INCLUDED', 'EXCLUDED')
            ORDER BY h.id, pr.id
            """, nativeQuery = true)
    List<HallPriceDetail> findEligibleHallPrices(
            @Param("regionCode") String regionCode,
            @Param("guestCount") int guestCount
    );

    // 2. 하객 수 조건을 충족하는 예식장 ID 조회
    @Query(value = """
            SELECT DISTINCT h.id
            FROM halls h
            JOIN vendors v
                ON v.id = h.vendor_id
            JOIN regions r
                ON r.id = v.region_id
            WHERE v.category = 'WEDDING_HALL'
              AND v.operating_status_raw = U&'\\C6B4\\C601'
              AND r.code = :regionCode
              AND EXISTS (
                  SELECT 1
                  FROM price_records pr
                  JOIN hall_price_details hpd
                      ON hpd.price_record_id = pr.id
                  WHERE pr.hall_id = h.id
                    AND hpd.capacity_max >= :guestCount
              )
            ORDER BY h.id
            """, nativeQuery = true)
    List<Long> findCandidateHallIds(
            @Param("regionCode") String regionCode,
            @Param("guestCount") int guestCount
    );

    // 3. 정보 부족 예식장 ID 조회
    @Query(value = """
            SELECT DISTINCT h.id
            FROM halls h
            JOIN vendors v
                ON v.id = h.vendor_id
            JOIN regions r
                ON r.id = v.region_id
            WHERE v.category = 'WEDDING_HALL'
              AND v.operating_status_raw = U&'\\C6B4\\C601'
              AND r.code = :regionCode

              -- 수용 인원 미확인 또는 조건 충족
              AND (
                  NOT EXISTS (
                      SELECT 1
                      FROM price_records pr
                      JOIN hall_price_details hpd
                          ON hpd.price_record_id = pr.id
                      WHERE pr.hall_id = h.id
                        AND hpd.capacity_max IS NOT NULL
                  )
                  OR EXISTS (
                      SELECT 1
                      FROM price_records pr
                      JOIN hall_price_details hpd
                          ON hpd.price_record_id = pr.id
                      WHERE pr.hall_id = h.id
                        AND hpd.capacity_max >= :guestCount
                  )
              )

              -- 필수 가격 정보가 완전한 기록이 없는 홀
              AND NOT EXISTS (
                  SELECT 1
                  FROM price_records pr
                  JOIN hall_price_details hpd
                      ON hpd.price_record_id = pr.id
                  WHERE pr.hall_id = h.id
                    AND hpd.capacity_max >= :guestCount
                    AND hpd.guaranteed_guests IS NOT NULL
                    AND hpd.rental_min IS NOT NULL
                    AND hpd.rental_max IS NOT NULL
                    AND hpd.meal_min IS NOT NULL
                    AND hpd.meal_max IS NOT NULL
                    AND pr.vat_status IN ('INCLUDED', 'EXCLUDED')
              )
            ORDER BY h.id
            """, nativeQuery = true)
    List<Long> findInformationInsufficientHallIds(
            @Param("regionCode") String regionCode,
            @Param("guestCount") int guestCount
    );
}
