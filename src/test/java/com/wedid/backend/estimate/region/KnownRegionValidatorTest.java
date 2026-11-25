package com.wedid.backend.estimate.region;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnownRegionValidatorTest {

    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final KnownRegionValidator validator = new KnownRegionValidator(regionRepository);

    @Test
    void DB_regions에_있는_코드만_통과한다() {
        when(regionRepository.existsByCode("SEOUL_GANGNAM")).thenReturn(true);
        when(regionRepository.existsByCode("INCHEON")).thenReturn(false);

        assertThat(validator.isValid("SEOUL_GANGNAM", null)).isTrue();
        assertThat(validator.isValid("INCHEON", null)).isFalse();
    }

    @Test
    void 빈_값은_NotBlank에_맡기고_DB를_조회하지_않는다() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(" ", null)).isTrue();
        verifyNoInteractions(regionRepository);
    }
}
