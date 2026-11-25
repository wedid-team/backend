package com.wedid.backend.estimate.region;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link KnownRegion} 검증: regions 테이블에 같은 code가 있으면 통과. */
public class KnownRegionValidator implements ConstraintValidator<KnownRegion, String> {

    private final RegionRepository regionRepository;

    public KnownRegionValidator(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @Override
    public boolean isValid(String regionCode, ConstraintValidatorContext context) {
        if (regionCode == null || regionCode.isBlank()) {
            return true;
        }
        return regionRepository.existsByCode(regionCode);
    }
}
