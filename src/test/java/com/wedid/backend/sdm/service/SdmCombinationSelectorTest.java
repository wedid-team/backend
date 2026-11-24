package com.wedid.backend.sdm.service;

import com.wedid.backend.sdm.service.SdmCombinationSelector.Candidate;
import com.wedid.backend.sdm.service.SdmCombinationSelector.Pick;
import com.wedid.backend.sdm.service.SdmCombinationSelector.Selection;
import com.wedid.backend.sdm.service.SdmCombinationSelector.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 금액은 테스트용 예시 값이다(원 단위). */
class SdmCombinationSelectorTest {

    private static final TierRange ANY = new TierRange(null, true, null, false);

    @Test
    void 조합은_카테고리마다_하나씩_골라_기본가를_더한다() {
        Selection selection = SdmCombinationSelector.select(
                List.of(c(1, 1_000_000), c(2, 2_000_000)),
                List.of(c(11, 1_500_000)),
                List.of(c(21, 500_000), c(22, 700_000)),
                ANY);

        assertThat(selection.combinationCount()).isEqualTo(4);
        assertThat(selection.combinationCountInTier()).isEqualTo(4);
        assertThat(selection.picks()).first().satisfies(pick -> assertThat(pick.total())
                .isEqualTo(pick.studio().price() + pick.dress().price() + pick.makeup().price()));
    }

    @Test
    void 예산대_범위_안_조합만_고르고_경계_포함_여부를_따른다() {
        // 합계: 300만, 320만, 340만, 360만
        List<Candidate> studios = List.of(c(1, 1_000_000), c(2, 1_200_000));
        List<Candidate> dresses = List.of(c(11, 1_500_000), c(12, 1_700_000));
        List<Candidate> makeups = List.of(c(21, 500_000));

        Selection exclusiveLower = SdmCombinationSelector.select(studios, dresses, makeups,
                new TierRange(3_000_000L, false, 3_400_000L, true));
        assertThat(exclusiveLower.combinationCountInTier()).isEqualTo(3);   // 320만 · 320만 · 340만
        assertThat(exclusiveLower.picks()).allSatisfy(pick -> assertThat(pick.total()).isBetween(3_000_001L, 3_400_000L));

        Selection inclusiveLower = SdmCombinationSelector.select(studios, dresses, makeups,
                new TierRange(3_000_000L, true, 3_400_000L, false));
        assertThat(inclusiveLower.combinationCountInTier()).isEqualTo(3);   // 300만 · 320만 · 320만
    }

    @Test
    void 하위_25_50_75퍼센트_지점에_가장_가까운_조합을_업체가_겹치지_않게_고른다() {
        // 스튜디오만 다르고 드레스 · 메이크업은 카테고리마다 3곳씩이라 업체가 겹치지 않는 조합 3개가 가능하다
        List<Candidate> studios = List.of(c(1, 1_000_000), c(2, 1_100_000), c(3, 1_200_000));
        List<Candidate> dresses = List.of(c(11, 1_000_000), c(12, 1_100_000), c(13, 1_200_000));
        List<Candidate> makeups = List.of(c(21, 1_000_000), c(22, 1_100_000), c(23, 1_200_000));

        Selection selection = SdmCombinationSelector.select(studios, dresses, makeups, ANY);

        // 합계 27개: 300만 1개, 310만 3개, 320만 6개, 330만 7개, 340만 6개, 350만 3개, 360만 1개
        assertThat(selection.targets()).containsEntry(Type.VALUE, 3_200_000L)
                .containsEntry(Type.BALANCED, 3_300_000L)
                .containsEntry(Type.PREMIUM, 3_400_000L);
        assertThat(selection.picks()).extracting(Pick::type).containsExactly(Type.VALUE, Type.BALANCED, Type.PREMIUM);
        assertThat(selection.picks()).extracting(Pick::total).containsExactly(3_200_000L, 3_300_000L, 3_400_000L);
        List<Long> vendorIds = selection.picks().stream()
                .flatMap(pick -> List.of(pick.studio().vendorId(), pick.dress().vendorId(), pick.makeup().vendorId()).stream())
                .toList();
        assertThat(vendorIds).doesNotHaveDuplicates();
    }

    @Test
    void 업체가_겹치지_않는_조합이_없으면_그_유형은_비우고_있는_만큼만_준다() {
        // 스튜디오가 한 곳뿐이라 첫 조합 뒤에는 고를 수 있는 조합이 없다
        Selection selection = SdmCombinationSelector.select(
                List.of(c(1, 1_000_000)),
                List.of(c(11, 1_500_000), c(12, 1_600_000)),
                List.of(c(21, 500_000), c(22, 600_000)),
                ANY);

        assertThat(selection.combinationCountInTier()).isEqualTo(4);
        assertThat(selection.picks()).extracting(Pick::type).containsExactly(Type.VALUE);
        assertThat(selection.targets()).hasSize(3);
    }

    @Test
    void 예산대_안에_조합이_하나뿐이면_한_개만_준다() {
        Selection selection = SdmCombinationSelector.select(
                List.of(c(1, 1_000_000), c(2, 3_000_000)),
                List.of(c(11, 1_500_000)),
                List.of(c(21, 500_000)),
                new TierRange(null, true, 3_000_000L, true));

        assertThat(selection.combinationCountInTier()).isEqualTo(1);
        assertThat(selection.picks()).singleElement().satisfies(pick -> {
            assertThat(pick.type()).isEqualTo(Type.VALUE);
            assertThat(pick.total()).isEqualTo(3_000_000L);
        });
    }

    @Test
    void 예산대_안에_조합이_없으면_고르지_않는다() {
        Selection selection = SdmCombinationSelector.select(
                List.of(c(1, 1_000_000)), List.of(c(11, 1_500_000)), List.of(c(21, 500_000)),
                new TierRange(5_000_000L, false, null, false));

        assertThat(selection.combinationCount()).isEqualTo(1);
        assertThat(selection.combinationCountInTier()).isZero();
        assertThat(selection.targets()).isEmpty();
        assertThat(selection.picks()).isEmpty();
    }

    @Test
    void 목표와의_차이가_같으면_공개_대행사_수_합이_많은_조합_그다음_업체_ID_순이다() {
        // 합계가 모두 300만으로 같다
        Selection byAgencies = SdmCombinationSelector.select(
                List.of(new Candidate(1, 1_000_000, 1), new Candidate(2, 1_000_000, 5)),
                List.of(c(11, 1_500_000)),
                List.of(c(21, 500_000)),
                ANY);
        assertThat(byAgencies.picks().get(0).studio().vendorId()).isEqualTo(2);

        Selection byId = SdmCombinationSelector.select(
                List.of(new Candidate(2, 1_000_000, 3), new Candidate(1, 1_000_000, 3)),
                List.of(c(11, 1_500_000)),
                List.of(c(21, 500_000)),
                ANY);
        assertThat(byId.picks().get(0).studio().vendorId()).isEqualTo(1);
    }

    private static Candidate c(long vendorId, long price) {
        return new Candidate(vendorId, price, 1);
    }
}
