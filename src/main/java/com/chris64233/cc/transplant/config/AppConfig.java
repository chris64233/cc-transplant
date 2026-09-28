package com.chris64233.cc.transplant.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    /** 统一时间来源（UTC），测试中可替换为可控时钟。 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
