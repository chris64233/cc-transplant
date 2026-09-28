package com.chris64233.cc.transplant.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.chris64233.cc.transplant.AbstractIntegrationTest;
import com.chris64233.cc.transplant.domain.AllocationRepository;
import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.DonorRepository;
import com.chris64233.cc.transplant.domain.OfferEventRepository;
import com.chris64233.cc.transplant.domain.OfferRepository;
import com.chris64233.cc.transplant.domain.OrganType;
import com.chris64233.cc.transplant.domain.RecipientRepository;
import com.chris64233.cc.transplant.domain.Urgency;
import com.jayway.jsonpath.JsonPath;

/**
 * REST 端到端测试：覆盖登记校验、唯一约束、完整分配/邀约/决定链路、
 * 幂等重放、409 冲突与统一错误结构。
 */
@AutoConfigureMockMvc
class AllocationApiTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DonorRepository donorRepository;
    @Autowired
    private RecipientRepository recipientRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private OfferRepository offerRepository;
    @Autowired
    private OfferEventRepository eventRepository;

    @BeforeEach
    void clean() {
        testDataCleaner.cleanAll();
    }

    @Test
    void validationErrors_returnUnifiedErrorStructure() throws Exception {
        // 缺少必填字段
        mockMvc.perform(post("/api/recipients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/recipients"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").exists());

        // 非法枚举值
        mockMvc.perform(post("/api/donors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalId":"D1","donorName":"n","organType":"NOT_AN_ORGAN","bloodType":"A"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void duplicateExternalId_returns409UnifiedStructure() throws Exception {
        String body = """
                {"externalId":"DUP","donorName":"供体","organType":"KIDNEY","bloodType":"O"}
                """;
        mockMvc.perform(post("/api/donors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/donors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("UNIQUE_CONSTRAINT"));
    }

    @Test
    void startAllocation_withoutCandidates_returns422_andCreatesNothing() throws Exception {
        registerDonor("D0", OrganType.HEART, BloodType.AB);
        registerRecipient("X", OrganType.HEART, BloodType.O, Urgency.HIGH,
                T0.minus(1, ChronoUnit.DAYS), true, "C");

        mockMvc.perform(post("/api/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"donorExternalId":"D0","offerTtlSeconds":60}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("NO_ELIGIBLE_CANDIDATE"));
        org.junit.jupiter.api.Assertions.assertEquals(0, allocationRepository.count());
    }

    @Test
    void fullWorkflow_issueExpireThenAccept_isIdempotent_andConflictOnReuse() throws Exception {
        registerDonor("D1", OrganType.KIDNEY, BloodType.A);
        registerRecipient("R1", OrganType.KIDNEY, BloodType.A, Urgency.URGENT,
                T0.minus(3, ChronoUnit.DAYS), true, "北京中心");
        registerRecipient("R2", OrganType.KIDNEY, BloodType.AB, Urgency.HIGH,
                T0.minus(2, ChronoUnit.DAYS), true, "上海中心");

        // 启动分配
        MvcResult startResult = mockMvc.perform(post("/api/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"donorExternalId":"D1","offerTtlSeconds":60}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.snapshot[0].position").value(1))
                .andExpect(jsonPath("$.snapshot[0].recipientExternalId").value("R1"))
                .andExpect(jsonPath("$.snapshot[1].recipientExternalId").value("R2"))
                .andReturn();
        String allocationNo = JsonPath.read(startResult.getResponse().getContentAsString(),
                "$.allocationNo");

        // 发第一份邀约
        MvcResult offerResult = mockMvc.perform(
                        post("/api/allocations/{no}/offers", allocationNo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.recipientExternalId").value("R1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn();
        String offerNo1 = JsonPath.read(offerResult.getResponse().getContentAsString(), "$.offerNo");

        // 未过期尝试 expire -> 409
        mockMvc.perform(post("/api/allocations/{no}/offers/{of}/expire", allocationNo, offerNo1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"EVT-EARLY"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OFFER_NOT_EXPIRED"));

        // 时间走过期点
        clock.advanceSeconds(61);

        // 过期后接受 -> 409
        mockMvc.perform(post("/api/allocations/{no}/offers/{of}/accept", allocationNo, offerNo1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"EVT-LATE-ACCEPT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("OFFER_EXPIRED"));

        // 过期处理（首次）
        mockMvc.perform(post("/api/allocations/{no}/offers/{of}/expire", allocationNo, offerNo1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"EVT-EXPIRE-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.offer.status").value("EXPIRED"));

        // 过期事件重放 -> 原结果
        mockMvc.perform(post("/api/allocations/{no}/offers/{of}/expire", allocationNo, offerNo1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"EVT-EXPIRE-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.offer.status").value("EXPIRED"));

        // 同一 eventId 改用于接受 -> 409
        mockMvc.perform(post("/api/allocations/{no}/offers/{of}/accept", allocationNo, offerNo1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"EVT-EXPIRE-1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_CONFLICT"));

        // 发第二份邀约并接受
        MvcResult offer2Result = mockMvc.perform(
                        post("/api/allocations/{no}/offers", allocationNo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.recipientExternalId").value("R2"))
                .andReturn();
        String offerNo2 = JsonPath.read(offer2Result.getResponse().getContentAsString(), "$.offerNo");

        mockMvc.perform(post("/api/allocations/{no}/offers/{of}/accept", allocationNo, offerNo2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"EVT-ACCEPT-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer.status").value("ACCEPTED"));

        // 接受事件重放
        mockMvc.perform(post("/api/allocations/{no}/offers/{of}/accept", allocationNo, offerNo2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":"EVT-ACCEPT-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        // 详情：终态、最终受者、两次决定、无当前邀约
        mockMvc.perform(get("/api/allocations/{no}", allocationNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.acceptedRecipientExternalId").value("R2"))
                .andExpect(jsonPath("$.currentOffer").doesNotExist())
                .andExpect(jsonPath("$.decisions.length()").value(2))
                .andExpect(jsonPath("$.snapshot.length()").value(2));

        // 终态再发邀约 -> 409
        mockMvc.perform(post("/api/allocations/{no}/offers", allocationNo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALLOCATION_FINISHED"));

        // 不存在资源 -> 404 统一结构
        mockMvc.perform(get("/api/allocations/NO-SUCH"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/allocations/NO-SUCH"));
    }
}
