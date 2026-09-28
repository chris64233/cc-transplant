package com.chris64233.cc.transplant.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试用可控时钟：固定起点，可在测试中按秒推进。
 */
@TestConfiguration
public class MutableClockConfig {

    public static final Instant START = Instant.parse("2026-09-28T08:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(START);
    }

    /**
     * 可变时钟，advance 后所有读取立即看到新时间。
     */
    public static class MutableClock extends Clock {

        private volatile Instant current;

        public MutableClock(Instant start) {
            this.current = start;
        }

        public void setInstant(Instant instant) {
            this.current = instant;
        }

        public void advanceSeconds(long seconds) {
            this.current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
