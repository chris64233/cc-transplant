package com.chris64233.cc.transplant.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** 统一时钟，便于测试中控制“现在”以验证邀约过期。 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
