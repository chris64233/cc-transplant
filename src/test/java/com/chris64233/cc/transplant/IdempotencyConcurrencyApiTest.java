package com.chris64233.cc.transplant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chris64233.cc.transplant.repo.CandidateRepository;
import com.chris64233.cc.transplant.service.AllocationService;
import com.chris64233.cc.transplant.support.ApiTestSupport;
import com.chris64233.cc.transplant.support.MutableClockConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 幂等事件、内容冲突 409、并发接受/过期最多一个生效。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Sql(scripts = "classpath:cleanup.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class IdempotencyConcurrencyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClockConfig.MutableClock clock;

    @Autowired
    private CandidateRepository candidateRepository;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        clock.setInstant(MutableClockConfig.START);
        api = new ApiTestSupport(mockMvc, objectMapper);
    }

    private String[] seedAndStart(int candidateCount, long ttl) throws Exception {
        api.donor("D1", "KIDNEY", "O");
        for (int i = 1; i <= candidateCount; i++) {
            api.candidate("C" + i, "KIDNEY", "O", 6 - i, "2026-01-0" + i + "T00:00:00Z", true);
        }
        JsonNode started = api.start("D1", ttl);
        return new String[] {
                started.get("allocationNo").asText(),
                started.get("currentOffer").get("offerNo").asText()};
    }

    @Test
    void sameEventReplaysOriginalResult() throws Exception {
        String[] started = seedAndStart(2, 300);
        String offer1 = started[1];

        JsonNode first = api.decision("decline", offer1, "evt-same");
        assertThat(first.get("replayed").asBoolean()).isFalse();
        assertThat(first.get("resultStatus").asText()).isEqualTo("DECLINED");

        JsonNode replay = api.decision("decline", offer1, "evt-same");
        assertThat(replay.get("replayed").asBoolean()).isTrue();
        assertThat(replay.get("resultStatus").asText()).isEqualTo("DECLINED");
        // 重放不产生新邀约、不产生重复事件
        mockMvc.perform(get("/api/allocations/" + started[0]))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.offers[0].status").value("DECLINED"));
    }

    @Test
    void sameEventIdWithDifferentActionReturns409() throws Exception {
        String[] started = seedAndStart(2, 300);
        String offer1 = started[1];

        api.decision("decline", offer1, "evt-reused");
        // 同一 eventId 用于不同决定类型
        MvcResult conflict = api.rawDecision("accept", offer1, "evt-reused");
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.read(conflict).get("code").asText()).isEqualTo("EVENT_CONFLICT");
    }

    @Test
    void sameEventIdOnDifferentOfferReturns409() throws Exception {
        String[] started = seedAndStart(2, 300);
        String offer1 = started[1];

        JsonNode declined = api.decision("decline", offer1, "evt-move-on");
        String offer2 = declined.get("nextOfferNo").asText();

        MvcResult conflict = api.rawDecision("accept", offer2, "evt-move-on");
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void acceptWhenCandidateDeactivatedReturnsRuleViolation() throws Exception {
        String[] started = seedAndStart(1, 300);
        String offer1 = started[1];

        candidateRepository.findByExternalRef("C1").ifPresent(c -> {
            c.setActive(false);
            candidateRepository.saveAndFlush(c);
        });

        MvcResult result = api.rawDecision("accept", offer1, "evt-inactive");
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(api.read(result).get("code").asText()).isEqualTo("RULE_VIOLATION");
    }

    @Test
    void concurrentAcceptsResultInExactlyOneWinner() throws Exception {
        String[] started = seedAndStart(1, 600);
        String allocationNo = started[0];
        String offer1 = started[1];

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String eventId = "evt-concurrent-accept-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return api.rawDecision("accept", offer1, eventId);
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        int success = 0;
        int conflict = 0;
        for (Future<MvcResult> f : futures) {
            MvcResult r = f.get(30, TimeUnit.SECONDS);
            int status = r.getResponse().getStatus();
            if (status >= 200 && status < 300) {
                success++;
                JsonNode body = api.read(r);
                assertThat(body.get("resultStatus").asText()).isEqualTo("ACCEPTED");
            } else {
                assertThat(status).isEqualTo(409);
                conflict++;
            }
        }
        pool.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(conflict).isEqualTo(threads - 1);

        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.recipientRef").value("C1"))
                .andExpect(jsonPath("$.offers[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void concurrentAcceptAndExpiryAtBoundaryResultsInExactlyOneOutcome() throws Exception {
        String[] started = seedAndStart(1, 300);
        String allocationNo = started[0];
        String offer1 = started[1];

        // 恰好到达过期边界：接受与过期在此时刻都可能被请求
        clock.advanceSeconds(300);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Future<MvcResult> acceptFuture = pool.submit(() -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            return api.rawDecision("accept", offer1, "evt-boundary-accept");
        });
        Future<MvcResult> expireFuture = pool.submit(() -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            return api.rawDecision("expire", offer1, "evt-boundary-expire");
        });
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        MvcResult acceptResult = acceptFuture.get(30, TimeUnit.SECONDS);
        MvcResult expireResult = expireFuture.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        int successCount = 0;
        MvcResult winner = null;
        MvcResult loser = null;
        for (MvcResult r : List.of(acceptResult, expireResult)) {
            if (r.getResponse().getStatus() < 300) {
                successCount++;
                winner = r;
            } else {
                assertThat(r.getResponse().getStatus()).isEqualTo(409);
                loser = r;
            }
        }
        assertThat(successCount).isEqualTo(1);
        assertThat(winner).isNotNull();
        assertThat(loser).isNotNull();

        // 最终状态必须与胜出者一致，无双重结果
        JsonNode winnerBody = api.read(winner);
        String expectedOfferStatus = winnerBody.get("decision").asText().equals("ACCEPT")
                ? "ACCEPTED" : "EXPIRED";
        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.offers[0].status").value(expectedOfferStatus))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void unknownOfferAndAllocationReturn404() throws Exception {
        mockMvc.perform(get("/api/allocations/NOPE")).andExpect(status().isNotFound());

        MvcResult r = api.rawDecision("accept", "OF-NOPE", "evt-nope");
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(api.read(r).get("code").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void systemExpiryEventIsIdempotentAcrossSweeps() throws Exception {
        String[] started = seedAndStart(1, 60);
        String offer1 = started[1];
        clock.advanceSeconds(61);

        JsonNode first = api.decision("expire", offer1, AllocationService.systemExpiryEventId(offer1));
        assertThat(first.get("replayed").asBoolean()).isFalse();
        JsonNode replay = api.decision("expire", offer1, AllocationService.systemExpiryEventId(offer1));
        assertThat(replay.get("replayed").asBoolean()).isTrue();
        assertThat(replay.get("resultStatus").asText()).isEqualTo("EXPIRED");
    }
}
