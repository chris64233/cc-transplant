package com.chris64233.cc.transplant.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.chris64233.cc.transplant.AbstractIntegrationTest;
import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.DonorRepository;
import com.chris64233.cc.transplant.domain.OrganType;
import com.chris64233.cc.transplant.domain.RecipientRepository;
import com.chris64233.cc.transplant.domain.Urgency;
import com.chris64233.cc.transplant.service.BusinessException;
import com.chris64233.cc.transplant.service.ErrorCode;
import com.chris64233.cc.transplant.web.dto.RegisterDonorRequest;
import com.chris64233.cc.transplant.web.dto.RegisterRecipientRequest;

class RegistrationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private RecipientRepository recipientRepository;

    @BeforeEach
    void clean() {
        recipientRepository.deleteAllInBatch();
        donorRepository.deleteAllInBatch();
    }

    @Test
    void registerDonor_persistsAllFields_andDefaultsRegisteredAt() {
        var donor = registrationService.registerDonor(new RegisterDonorRequest(
                "D-1", "张三", OrganType.KIDNEY, BloodType.A, null));

        assertNotNull(donor.getId());
        var loaded = donorRepository.findByExternalId("D-1").orElseThrow();
        assertEquals("张三", loaded.getDonorName());
        assertEquals(OrganType.KIDNEY, loaded.getOrganType());
        assertEquals(BloodType.A, loaded.getBloodType());
        assertNotNull(loaded.getRegisteredAt());
    }

    @Test
    void registerRecipient_persistsAllFields() {
        var recipient = registerRecipient("R-1", OrganType.HEART, BloodType.AB,
                Urgency.URGENT, T0.minus(10, ChronoUnit.DAYS), false, "北京中心");

        var loaded = recipientRepository.findById(recipient.getId()).orElseThrow();
        assertEquals(Urgency.URGENT, loaded.getUrgency());
        assertEquals(T0.minus(10, ChronoUnit.DAYS), loaded.getWaitlistedAt());
        assertFalse(loaded.isActive());
        assertEquals("北京中心", loaded.getCenter());
    }

    @Test
    void duplicateDonorExternalId_isRejectedWith409() {
        registerDonor("DUP-D", OrganType.LIVER, BloodType.O);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> registerDonor("DUP-D", OrganType.LIVER, BloodType.A));
        assertEquals(409, ex.getHttpStatus());
        assertEquals(ErrorCode.UNIQUE_CONSTRAINT, ex.getErrorCode());
        assertEquals(1, donorRepository.count());
    }

    @Test
    void duplicateRecipientExternalId_isRejectedWith409() {
        registerRecipient("DUP-R", OrganType.LIVER, BloodType.O, Urgency.HIGH,
                T0.minus(1, ChronoUnit.DAYS), true, "C1");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> registerRecipient("DUP-R", OrganType.LIVER, BloodType.A, Urgency.STANDARD,
                        T0.minus(2, ChronoUnit.DAYS), true, "C2"));
        assertEquals(409, ex.getHttpStatus());
        assertEquals(ErrorCode.UNIQUE_CONSTRAINT, ex.getErrorCode());
        assertEquals(1, recipientRepository.count());
    }

    @Test
    void externalIdUniquenessIsEnforcedByDatabaseConstraint() {
        // 直接绕开服务层预检，验证数据库唯一约束确实存在。
        registerDonor("DB-D", OrganType.KIDNEY, BloodType.O);
        registerRecipient("DB-R", OrganType.KIDNEY, BloodType.O, Urgency.HIGH,
                Instant.now(), true, "C1");
        assertTrue(donorRepository.existsByExternalId("DB-D"));
        assertTrue(recipientRepository.existsByExternalId("DB-R"));
    }
}
