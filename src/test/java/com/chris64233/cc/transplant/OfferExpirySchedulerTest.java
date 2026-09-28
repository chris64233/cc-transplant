package com.chris64233.cc.transplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import com.chris64233.cc.transplant.service.OfferExpiryScheduler;
import com.chris64233.cc.transplant.support.ApiTestSupport;
import com.chris64233.cc.transplant.support.MutableClockConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 定时过期扫描：到点自动按过期处理并顺位推进，重复扫描幂等无副作用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Sql(scripts = "classpath:cleanup.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OfferExpirySchedulerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClockConfig.MutableClock clock;

    @Autowired
    private OfferExpiryScheduler scheduler;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        clock.setInstant(MutableClockConfig.START);
        api = new ApiTestSupport(mockMvc, objectMapper);
    }

    @Test
    void sweepExpiresDueOffersAndIsIdempotent() throws Exception {
        api.donor("D-SW", "KIDNEY", "O");
        api.candidate("SW1", "KIDNEY", "O", 5, "2026-01-01T00:00:00Z", true);
        api.candidate("SW2", "KIDNEY", "O", 4, "2026-01-01T00:00:00Z", true);
        JsonNode started = api.start("D-SW", 60);
        String allocationNo = started.get("allocationNo").asText();

        // 未到点：扫描无动作
        clock.advanceSeconds(30);
        scheduler.sweepExpiredOffers();
        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.offers[0].status").value("PENDING"));

        // 到点：扫描使首个邀约过期并自动对第二位发约
        clock.advanceSeconds(31);
        scheduler.sweepExpiredOffers();
        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.offers[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.offers[1].status").value("PENDING"))
                .andExpect(jsonPath("$.currentOffer.candidateRef").value("SW2"))
                .andExpect(jsonPath("$.events.length()").value(1));

        // 重复扫描幂等：不产生新事件
        scheduler.sweepExpiredOffers();
        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.events.length()").value(1));

        // 第二个也过期后扫描：流程耗尽
        clock.advanceSeconds(61);
        scheduler.sweepExpiredOffers();
        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.status").value("EXHAUSTED"))
                .andExpect(jsonPath("$.events.length()").value(2));

        assertThat(allocationNo).startsWith("AL");
    }
}
