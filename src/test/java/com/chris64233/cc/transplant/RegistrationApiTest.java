package com.chris64233.cc.transplant;

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
import org.springframework.test.context.jdbc.Sql;

import com.chris64233.cc.transplant.support.MutableClockConfig;

/**
 * 登记接口的输入完整性校验与外部编号唯一性。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClockConfig.class)
@Sql(scripts = "classpath:cleanup.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RegistrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registersDonorWithAllFields() throws Exception {
        mockMvc.perform(post("/api/registrations/donors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalRef":"D-1","organType":"KIDNEY","bloodType":"O",
                                 "centerCode":"CN-BJ"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalRef").value("D-1"))
                .andExpect(jsonPath("$.organType").value("KIDNEY"))
                .andExpect(jsonPath("$.bloodType").value("O"))
                .andExpect(jsonPath("$.registeredAt").exists());
    }

    @Test
    void rejectsDonorWithMissingFields() throws Exception {
        mockMvc.perform(post("/api/registrations/donors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalRef":"","organType":"KIDNEY","centerCode":"CN-BJ"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='bloodType')]").exists());
    }

    @Test
    void rejectsDuplicateDonorRef() throws Exception {
        String body = """
                {"externalRef":"D-DUP","organType":"LIVER","bloodType":"A","centerCode":"CN-SH"}
                """;
        mockMvc.perform(post("/api/registrations/donors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/registrations/donors").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REF"));
    }

    @Test
    void rejectsCandidateWithUrgencyOutOfRangeAndMissingFields() throws Exception {
        mockMvc.perform(post("/api/registrations/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalRef":"C-BAD","organType":"HEART","bloodType":"AB",
                                 "urgency":9,"waitlistedAt":"2026-09-01T00:00:00Z",
                                 "active":true,"centerCode":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[?(@.field=='urgency')]").exists())
                .andExpect(jsonPath("$.details[?(@.field=='centerCode')]").exists());
    }

    @Test
    void rejectsDuplicateCandidateRef() throws Exception {
        String body = """
                {"externalRef":"C-DUP","organType":"KIDNEY","bloodType":"B","urgency":3,
                 "waitlistedAt":"2026-09-01T00:00:00Z","active":true,"centerCode":"CN-BJ"}
                """;
        mockMvc.perform(post("/api/registrations/candidates")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/registrations/candidates")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EXTERNAL_REF"));
    }

    @Test
    void rejectsUnknownEnumValue() throws Exception {
        mockMvc.perform(post("/api/registrations/donors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalRef":"D-X","organType":"BRAIN","bloodType":"O",
                                 "centerCode":"CN-BJ"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
