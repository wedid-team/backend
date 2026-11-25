package com.wedid.backend.sdm.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스튜디오 · 드레스 · 메이크업에서 하나씩 골라 조합을 만들고, 고른 예산대 안에서 최대 3개를 고른다 (E-20).
 * <ol>
 *   <li>조합 합계 = 세 업체 기본가의 합. 추가 옵션은 넣지 않는다.</li>
 *   <li>예산대 범위 안 조합만 남긴다. 다른 예산대 조합으로 대신 채우지 않는다.</li>
 *   <li>남은 조합의 합계를 낮은 순으로 놓고 하위 25% · 50% · 75% 지점 금액을 목표로 정한다
 *       (n개 중 floor(q × (n − 1))번째 값).</li>
 *   <li>실속형(25%) → 균형형(50%) → 프리미엄형(75%) 순서로, 목표에 가장 가까운 조합을 하나씩 고른다.
 *       앞에서 고른 조합과 업체가 하나라도 겹치면 후보에서 뺀다.</li>
 *   <li>차이가 같으면 공개 대행사 수 합이 많은 조합, 그다음 스튜디오 · 드레스 · 메이크업 업체 ID가 작은 조합.</li>
 *   <li>조건을 만족하는 조합이 없으면 그 유형은 비운다. 억지로 채우지 않는다 (3개 미만이면 있는 만큼).</li>
 * </ol>
 */
public final class SdmCombinationSelector {

    public enum Type {
        VALUE("실속형", 0.25),
        BALANCED("균형형", 0.50),
        PREMIUM("프리미엄형", 0.75);

        private final String label;
        private final double quantile;

        Type(String label, double quantile) {
            this.label = label;
            this.quantile = quantile;
        }

        public String label() {
            return label;
        }

        public double quantile() {
            return quantile;
        }
    }

    /** 조합 후보 업체. price는 원 단위 기본가. */
    public record Candidate(long vendorId, long price, int agencyCount) {
    }

    public record Pick(Type type, Candidate studio, Candidate dress, Candidate makeup, long total, long target) {
    }

    public record Selection(
            long combinationCount,
            long combinationCountInTier,
            Map<Type, Long> targets,
            List<Pick> picks
    ) {
    }

    private SdmCombinationSelector() {
    }

    public static Selection select(List<Candidate> studios, List<Candidate> dresses, List<Candidate> makeups, TierRange tier) {
        long combinationCount = (long) studios.size() * dresses.size() * makeups.size();
        long[] sums = sortedTierSums(studios, dresses, makeups, tier);
        if (sums.length == 0) {
            return new Selection(combinationCount, 0, Map.of(), List.of());
        }

        Map<Type, Long> targets = new LinkedHashMap<>();
        List<Pick> picks = new ArrayList<>();
        Set<Long> usedVendors = new HashSet<>();
        for (Type type : Type.values()) {
            long target = quantile(sums, type.quantile());
            targets.put(type, target);
            Pick pick = closest(type, target, studios, dresses, makeups, tier, usedVendors);
            if (pick != null) {
                picks.add(pick);
                usedVendors.add(pick.studio().vendorId());
                usedVendors.add(pick.dress().vendorId());
                usedVendors.add(pick.makeup().vendorId());
            }
        }
        return new Selection(combinationCount, sums.length, targets, picks);
    }

    /** 예산대 안 조합 합계를 낮은 순으로. */
    public static long[] sortedTierSums(List<Candidate> studios, List<Candidate> dresses, List<Candidate> makeups, TierRange tier) {
        long[] sums = inTierSums(studios, dresses, makeups, tier);
        Arrays.sort(sums);
        return sums;
    }

    /** n개 중 floor(q × (n − 1))번째 값. sorted는 비어 있지 않아야 한다. */
    public static long quantile(long[] sorted, double q) {
        return sorted[(int) Math.floor(q * (sorted.length - 1))];
    }

    private static long[] inTierSums(List<Candidate> studios, List<Candidate> dresses, List<Candidate> makeups, TierRange tier) {
        int count = 0;
        for (Candidate s : studios) {
            for (Candidate d : dresses) {
                long sd = s.price() + d.price();
                for (Candidate m : makeups) {
                    if (tier.contains(sd + m.price())) {
                        count++;
                    }
                }
            }
        }
        long[] sums = new long[count];
        int i = 0;
        for (Candidate s : studios) {
            for (Candidate d : dresses) {
                long sd = s.price() + d.price();
                for (Candidate m : makeups) {
                    long total = sd + m.price();
                    if (tier.contains(total)) {
                        sums[i++] = total;
                    }
                }
            }
        }
        return sums;
    }

    private static Pick closest(
            Type type, long target,
            List<Candidate> studios, List<Candidate> dresses, List<Candidate> makeups,
            TierRange tier, Set<Long> usedVendors
    ) {
        Pick best = null;
        for (Candidate s : studios) {
            if (usedVendors.contains(s.vendorId())) {
                continue;
            }
            for (Candidate d : dresses) {
                if (usedVendors.contains(d.vendorId())) {
                    continue;
                }
                for (Candidate m : makeups) {
                    if (usedVendors.contains(m.vendorId())) {
                        continue;
                    }
                    long total = s.price() + d.price() + m.price();
                    if (!tier.contains(total)) {
                        continue;
                    }
                    Pick candidate = new Pick(type, s, d, m, total, target);
                    if (best == null || better(candidate, best)) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    private static boolean better(Pick a, Pick b) {
        long diffA = Math.abs(a.total() - a.target());
        long diffB = Math.abs(b.total() - b.target());
        if (diffA != diffB) {
            return diffA < diffB;
        }
        int agenciesA = a.studio().agencyCount() + a.dress().agencyCount() + a.makeup().agencyCount();
        int agenciesB = b.studio().agencyCount() + b.dress().agencyCount() + b.makeup().agencyCount();
        if (agenciesA != agenciesB) {
            return agenciesA > agenciesB;
        }
        int byStudio = Long.compare(a.studio().vendorId(), b.studio().vendorId());
        if (byStudio != 0) {
            return byStudio < 0;
        }
        int byDress = Long.compare(a.dress().vendorId(), b.dress().vendorId());
        if (byDress != 0) {
            return byDress < 0;
        }
        return a.makeup().vendorId() < b.makeup().vendorId();
    }
}
