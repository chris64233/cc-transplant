package com.chris64233.cc.transplant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.jdbc.Sql;

import com.chris64233.cc.transplant.support.MutableClockConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 启动分配：血型相容性过滤、排序规则、不可变快照、无合格候选人失败。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Sql(scripts = "classpath:cleanup.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AllocationStartApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void donor(String ref, String organ, String blood) throws Exception {
        mockMvc.perform(post("/api/registrations/donors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                """
                                {"externalRef":"%s","organType":"%s","bloodType":"%s","centerCode":"C1"}
                                """, ref, organ, blood)))
                .andExpect(status().isCreated());
    }

    private void candidate(String ref, String organ, String blood, int urgency, String waitlisted,
                           boolean active) throws Exception {
        mockMvc.perform(post("/api/registrations/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                """
                                {"externalRef":"%s","organType":"%s","bloodType":"%s","urgency":%d,
                                 "waitlistedAt":"%s","active":%s,"centerCode":"C1"}
                                """, ref, organ, blood, urgency, waitlisted, active)))
                .andExpect(status().isCreated());
    }

    @Test
    void failsWhenNoCompatibleCandidate() throws Exception {
        donor("D-NONE", "KIDNEY", "AB");
        // AB 型供体只能给 AB；这里只登记 A/O 受者
        candidate("CA1", "KIDNEY", "A", 5, "2026-01-01T00:00:00Z", true);
        candidate("CO1", "KIDNEY", "O", 5, "2026-01-01T00:00:00Z", true);

        mockMvc.perform(post("/api/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"donorExternalRef":"D-NONE","offerTtlSeconds":300}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_VIOLATION"));

        // 失败后不能残留空流程
        mockMvc.perform(get("/api/allocations/AL-whatever")).andExpect(status().isNotFound());
    }

    @Test
    void ignoresInactiveAndWrongOrganCandidates() throws Exception {
        donor("D-ACT", "LIVER", "O");
        candidate("ACT1", "LIVER", "A", 2, "2026-02-01T00:00:00Z", false); // 停用
        candidate("ACT2", "KIDNEY", "O", 5, "2026-01-01T00:00:00Z", true);  // 器官不符
        candidate("ACT3", "LIVER", "O", 1, "2026-03-01T00:00:00Z", true);   // 唯一合格

        MvcResult result = mockMvc.perform(post("/api/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"donorExternalRef":"D-ACT","offerTtlSeconds":300}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateCount").value(1))
                .andExpect(jsonPath("$.currentOffer.candidateRef").value("ACT3"))
                .andReturn();

        String allocationNo = json(result).get("allocationNo").asText();
        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.snapshot.length()").value(1))
                .andExpect(jsonPath("$.snapshot[0].candidateRef").value("ACT3"));
    }

    @Test
    void ordersByUrgencyThenWaitTimeThenRefAndFiltersByBloodCompatibility() throws Exception {
        // O 型供肾：所有血型可受
        donor("D-ORD", "KIDNEY", "O");
        candidate("ORD-A", "KIDNEY", "A", 3, "2026-01-10T00:00:00Z", true);
        candidate("ORD-B", "KIDNEY", "B", 5, "2026-02-10T00:00:00Z", true);   // 紧急最高
        candidate("ORD-C", "KIDNEY", "AB", 5, "2026-01-01T00:00:00Z", true);  // 同级，等待更久
        candidate("ORD-D", "KIDNEY", "O", 3, "2026-01-01T00:00:00Z", true);   // 同级同时，编号小
        candidate("ORD-E", "KIDNEY", "A", 3, "2026-01-01T00:00:00Z", false);  // 停用，不入快照

        MvcResult result = mockMvc.perform(post("/api/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"donorExternalRef":"D-ORD","offerTtlSeconds":300}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String allocationNo = json(result).get("allocationNo").asText();

        mockMvc.perform(get("/api/allocations/" + allocationNo))
                .andExpect(jsonPath("$.snapshot.length()").value(4))
                .andExpect(jsonPath("$.snapshot[0].candidateRef").value("ORD-C"))
                .andExpect(jsonPath("$.snapshot[1].candidateRef").value("ORD-B"))
                .andExpect(jsonPath("$.snapshot[2].candidateRef").value("ORD-D"))
                .andExpect(jsonPath("$.snapshot[3].candidateRef").value("ORD-A"))
                .andExpect(jsonPath("$.snapshot[0].state").value("OFFERED"))
                .andExpect(jsonPath("$.snapshot[1].state").value("WAITING"));
    }

    @Test
    void cannotStartAllocationTwiceForSameDonor() throws Exception {
        donor("D-TWICE", "HEART", "A");
        candidate("TW1", "HEART", "A", 1, "2026-01-01T00:00:00Z", true);

        String body = """
                {"donorExternalRef":"D-TWICE","offerTtlSeconds":300}
                """;
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/allocations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_CONFLICT"));
    }
}
