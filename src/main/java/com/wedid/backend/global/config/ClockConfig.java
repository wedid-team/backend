package com.wedid.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 만료 시각 계산 등에 쓰는 시계. 테스트에서 고정 시계로 바꿀 수 있도록 빈으로 둔다. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
