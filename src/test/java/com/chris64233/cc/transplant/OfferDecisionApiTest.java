package com.chris64233.cc.transplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chris64233.cc.transplant.support.ApiTestSupport;
import com.chris64233.cc.transplant.support.MutableClockConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 邀约决定流转：接受终态、拒绝后顺位推进、过期处理、停用跳过、流程耗尽。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Sql(scripts = "classpath:cleanup.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OfferDecisionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClockConfig.MutableClock clock;

    @Autowired
    private com.chris64233.cc.transplant.repo.CandidateRepository candidateRepository;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        clock.setInstant(MutableClockConfig.START);
        api = new ApiTestSupport(mockMvc, objectMapper);
    }

    private void seedThreeCandidates() throws Exception {
        api.donor("D1", "KIDNEY", "O");
        api.candidate("C1", "KIDNEY", "A", 5, "2026-01-01T00:00:00Z", true);
        api.candidate("C2", "KIDNEY", "B", 4, "2026-01-01T00:00:00Z", true);
        api.candidate("C3", "KIDNEY", "O", 3, "2026-01-01T00:00:00Z", true);
    }

    @Test
    void acceptWithinValidityAllocatesOrganAndBlocksOthers() throws Exception {
        seedThreeCandidates();
        JsonNode started = api.start("D1", 300);
        String allocationNo = started.get("allocationNo").asText();
        String offerNo = started.get("currentOffer").get("offerNo").asText();

        clock.advanceSeconds(100);
        JsonNode accepted = api.decision("accept", offerNo, "evt-accept-1");
        assertThat(accepted.get("resultStatus").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("replayed").asBoolean()).isFalse();

        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.recipientRef").value("C1"))
                .andExpect(jsonPath("$.recipientPosition").value(0))
                .andExpect(jsonPath("$.currentOffer").doesNotExist())
                .andExpect(jsonPath("$.snapshot[0].state").value("ACCEPTED"))
                .andExpect(jsonPath("$.finishedAt").exists());

        // 其余候选人不能再决定：流程已结束，任何决定均 409
        MvcResult secondAccept = api.rawDecision("decline", offerNo, "evt-decline-after");
        assertThat(secondAccept.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.read(secondAccept).get("code").asText()).isEqualTo("EVENT_CONFLICT");
    }

    @Test
    void declineMovesToNextCandidateWithIncrementingSequence() throws Exception {
        seedThreeCandidates();
        JsonNode started = api.start("D1", 300);
        String allocationNo = started.get("allocationNo").asText();
        String offer1 = started.get("currentOffer").get("offerNo").asText();

        JsonNode declined = api.decision("decline", offer1, "evt-decline-1");
        assertThat(declined.get("resultStatus").asText()).isEqualTo("DECLINED");
        String offer2 = declined.get("nextOfferNo").asText();
        assertThat(offer2).isNotEqualTo(offer1);

        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.currentOffer.candidateRef").value("C2"))
                .andExpect(jsonPath("$.currentOffer.sequenceNo").value(2))
                .andExpect(jsonPath("$.snapshot[0].state").value("DECLINED"))
                .andExpect(jsonPath("$.snapshot[1].state").value("OFFERED"))
                // 一次只有一个有效邀约：共两条邀约，前一条已拒绝，当前为 PENDING
                .andExpect(jsonPath("$.offers.length()").value(2))
                .andExpect(jsonPath("$.offers[0].status").value("DECLINED"))
                .andExpect(jsonPath("$.offers[1].status").value("PENDING"));

        JsonNode declined2 = api.decision("decline", offer2, "evt-decline-2");
        String offer3 = declined2.get("nextOfferNo").asText();

        JsonNode accepted = api.decision("accept", offer3, "evt-accept-3");
        assertThat(accepted.get("resultStatus").asText()).isEqualTo("ACCEPTED");

        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.recipientRef").value("C3"))
                .andExpect(jsonPath("$.offers.length()").value(3))
                .andExpect(jsonPath("$.offers[2].sequenceNo").value(3));
    }

    @Test
    void acceptAfterExpiryRejectedAndExpireBeforeTtlRejected() throws Exception {
        seedThreeCandidates();
        JsonNode started = api.start("D1", 300);
        String offer1 = started.get("currentOffer").get("offerNo").asText();

        // 未到过期时间不能按过期处理
        clock.advanceSeconds(10);
        MvcResult earlyExpire = api.rawDecision("expire", offer1, "evt-expire-early");
        assertThat(earlyExpire.getResponse().getStatus()).isEqualTo(409);

        // 有效期内可以拒绝
        JsonNode declined = api.decision("decline", offer1, "evt-decline-early");
        String offer2 = declined.get("nextOfferNo").asText();

        // 到达过期时间后不能接受（新邀约在拒绝时重新计时，推进到其过期边界之后）
        clock.advanceSeconds(301);
        MvcResult lateAccept = api.rawDecision("accept", offer2, "evt-late-accept");
        assertThat(lateAccept.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.read(lateAccept).get("code").asText()).isEqualTo("RULE_VIOLATION");

        // 过期后顺位到第三位，最终接受
        JsonNode expired = api.decision("expire", offer2, "evt-expire-2");
        assertThat(expired.get("resultStatus").asText()).isEqualTo("EXPIRED");
        String offer3 = expired.get("nextOfferNo").asText();
        assertThat(offer3).isNotNull();

        JsonNode accepted = api.decision("accept", offer3, "evt-accept-3");
        assertThat(accepted.get("resultStatus").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void expiredOffersAdvanceUntilExhausted() throws Exception {
        api.donor("D-EX", "KIDNEY", "O");
        api.candidate("E1", "KIDNEY", "O", 5, "2026-01-01T00:00:00Z", true);
        api.candidate("E2", "KIDNEY", "O", 4, "2026-01-01T00:00:00Z", true);

        JsonNode started = api.start("D-EX", 60);
        String allocationNo = started.get("allocationNo").asText();
        String offer1 = started.get("currentOffer").get("offerNo").asText();

        clock.advanceSeconds(61);
        JsonNode exp1 = api.decision("expire", offer1, "evt-exp-e1");
        String offer2 = exp1.get("nextOfferNo").asText();
        assertThat(offer2).isNotNull();

        clock.advanceSeconds(61);
        JsonNode exp2 = api.decision("expire", offer2, "evt-exp-e2");
        assertThat(exp2.get("nextOfferNo").isNull()).isTrue();

        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.status").value("EXHAUSTED"))
                .andExpect(jsonPath("$.currentOffer").doesNotExist())
                .andExpect(jsonPath("$.recipientRef").doesNotExist())
                .andExpect(jsonPath("$.snapshot[0].state").value("EXPIRED"))
                .andExpect(jsonPath("$.snapshot[1].state").value("EXPIRED"));
    }

    @Test
    void inactiveCandidateAtOfferTimeIsSkipped() throws Exception {
        // 停用发生在分配启动之后：快照不可变，发约时按当前激活状态跳过
        api.donor("D-SKIP", "KIDNEY", "O");
        api.candidate("S1", "KIDNEY", "O", 5, "2026-01-01T00:00:00Z", true);
        api.candidate("S2", "KIDNEY", "O", 4, "2026-01-01T00:00:00Z", true);
        api.candidate("S3", "KIDNEY", "O", 3, "2026-01-01T00:00:00Z", true);

        JsonNode started = api.start("D-SKIP", 300);
        String allocationNo = started.get("allocationNo").asText();
        String offer1 = started.get("currentOffer").get("offerNo").asText();

        // 停用 S2：快照不可变，发约时按当前激活状态跳过（直接经由仓库模拟运营停用）
        deactivateCandidate("S2");

        JsonNode declined = api.decision("decline", offer1, "evt-skip-decline");
        // S2 被跳过，直接对 S3 发约
        assertThat(declined.get("nextOfferNo").isNull()).isFalse();

        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.currentOffer.candidateRef").value("S3"))
                .andExpect(jsonPath("$.snapshot[1].state").value("DEACTIVATED"))
                .andExpect(jsonPath("$.offers.length()").value(2)); // 没有给 S2 发约，不占邀约序号
    }

    private void deactivateCandidate(String ref) {
        candidateRepository.findByExternalRef(ref).ifPresent(c -> {
            c.setActive(false);
            candidateRepository.saveAndFlush(c);
        });
    }
}
