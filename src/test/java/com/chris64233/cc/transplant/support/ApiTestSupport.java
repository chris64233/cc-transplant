package com.chris64233.cc.transplant.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 测试常用构造/调用辅助。
 */
public class ApiTestSupport {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    public ApiTestSupport(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    public void donor(String ref, String organ, String blood) throws Exception {
        mockMvc.perform(post("/api/registrations/donors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"externalRef\":\"%s\",\"organType\":\"%s\",\"bloodType\":\"%s\","
                                        + "\"centerCode\":\"C1\"}", ref, organ, blood)))
                .andExpect(status().isCreated());
    }

    public void candidate(String ref, String organ, String blood, int urgency, String waitlisted,
                          boolean active) throws Exception {
        mockMvc.perform(post("/api/registrations/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"externalRef\":\"%s\",\"organType\":\"%s\",\"bloodType\":\"%s\","
                                        + "\"urgency\":%d,\"waitlistedAt\":\"%s\",\"active\":%s,"
                                        + "\"centerCode\":\"C1\"}",
                                ref, organ, blood, urgency, waitlisted, active)))
                .andExpect(status().isCreated());
    }

    /** 启动分配并返回响应 JSON。 */
    public JsonNode start(String donorRef, long ttlSeconds) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/allocations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"donorExternalRef\":\"%s\",\"offerTtlSeconds\":%d}",
                                donorRef, ttlSeconds)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result);
    }

    public JsonNode decision(String endpoint, String offerNo, String eventId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/allocations/offers/" + offerNo + "/" + endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"offerNo\":\"%s\",\"eventId\":\"%s\"}", offerNo, eventId)))
                .andReturn();
        return read(result);
    }

    public MvcResult rawDecision(String endpoint, String offerNo, String eventId) throws Exception {
        return mockMvc.perform(post("/api/allocations/offers/" + offerNo + "/" + endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"offerNo\":\"%s\",\"eventId\":\"%s\"}", offerNo, eventId)))
                .andReturn();
    }

    public JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    public MockMvc mockMvc() {
        return mockMvc;
    }
}
