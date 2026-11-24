package com.wedid.backend.plan.service;

import com.wedid.backend.estimate.entity.CalculationPolicy;
import com.wedid.backend.estimate.entity.SdmBudgetBand;
import com.wedid.backend.estimate.repository.CalculationPolicyRepository;
import com.wedid.backend.estimate.repository.SdmBudgetBandRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 계획 계산에 쓸 정책 버전과 스드메 예산대를 고른다.
 * ACTIVE 정책이 있으면 그것을, 없으면 가장 최근 DRAFT 정책을 쓴다.
 * 예산대 경계(Q-05)가 확정되기 전에는 DRAFT 정책만 있으므로 DRAFT로도 계획을 만들 수 있어야 한다.
 */
@Component
public class PlanPolicyResolver {

    static final String FALLBACK_DEFAULT_BAND_CODE = "STANDARD";

    private final CalculationPolicyRepository policyRepository;
    private final SdmBudgetBandRepository bandRepository;
    private final JsonMapper jsonMapper;

    public PlanPolicyResolver(
            CalculationPolicyRepository policyRepository,
            SdmBudgetBandRepository bandRepository,
            JsonMapper jsonMapper
    ) {
        this.policyRepository = policyRepository;
        this.bandRepository = bandRepository;
        this.jsonMapper = jsonMapper;
    }

    public CalculationPolicy currentPolicy() {
        return policyRepository.findFirstByStatusOrderByIdDesc(CalculationPolicy.ACTIVE)
                .or(() -> policyRepository.findFirstByStatusOrderByIdDesc(CalculationPolicy.DRAFT))
                .orElseThrow(() -> new IllegalStateException(
                        "계산 정책이 없습니다. sql/034_seed_plan_reference_data.sql 적용 여부를 확인하세요."));
    }

    /**
     * @param requestedCode 요청한 예산대. null이면 fallbackCode, 그것도 null이면 정책 기본값(E-10)
     * @param fallbackCode  기존 계획의 예산대 코드. 새 계획이면 null
     */
    public SdmBudgetBand band(CalculationPolicy policy, String requestedCode, String fallbackCode) {
        String code = requestedCode != null ? requestedCode
                : fallbackCode != null ? fallbackCode
                : defaultBandCode(policy);

        return bandRepository.findByPolicyIdAndCode(policy.getId(), code)
                .orElseThrow(() -> new IllegalStateException(
                        "정책 " + policy.getVersion() + "에 스드메 예산대 " + code + "가 없습니다."));
    }

    /** 정책의 기본 스드메 예산대 코드 (E-10, 설정이 없으면 STANDARD) */
    public String defaultBandCode(CalculationPolicy policy) {
        JsonNode node = jsonMapper.readTree(policy.getSettings()).path("defaultSdmBandCode");
        return node.isString() ? node.asString() : FALLBACK_DEFAULT_BAND_CODE;
    }
}
