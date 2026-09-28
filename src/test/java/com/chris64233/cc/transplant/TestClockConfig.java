package com.chris64233.cc.transplant;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 用 {@link MutableClock} 替换生产环境的系统时钟，便于过期相关测试。
 */
@TestConfiguration
public class TestClockConfig {

    public static final Instant FIXED_NOW = Instant.parse("2026-09-28T08:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(FIXED_NOW);
    }
}
