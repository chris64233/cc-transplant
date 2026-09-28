package com.chris64233.cc.transplant;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.cc.transplant.domain.BloodType;
import com.chris64233.cc.transplant.domain.OrganType;
import com.chris64233.cc.transplant.domain.Recipient;
import com.chris64233.cc.transplant.domain.RecipientRepository;
import com.chris64233.cc.transplant.service.RegistrationService;
import com.chris64233.cc.transplant.web.dto.RegisterDonorRequest;
import com.chris64233.cc.transplant.web.dto.RegisterRecipientRequest;

/**
 * 服务层集成测试基类：挂接可控时钟并提供登记辅助方法。
 */
@SpringBootTest(classes = {CcTransplantApplication.class, TestClockConfig.class})
public abstract class AbstractIntegrationTest {

    protected static final Instant T0 = TestClockConfig.FIXED_NOW;

    @Autowired
    protected RegistrationService registrationService;

    @Autowired
    protected RecipientRepository recipientRepository;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected TestDataCleaner testDataCleaner;

    @org.junit.jupiter.api.BeforeEach
    void resetClock() {
        clock.setInstant(T0);
    }

    protected void registerDonor(String externalId, OrganType organ, BloodType blood) {
        registrationService.registerDonor(new RegisterDonorRequest(
                externalId, "供体-" + externalId, organ, blood, T0));
    }

    protected Recipient registerRecipient(String externalId, OrganType organ, BloodType blood,
                                          com.chris64233.cc.transplant.domain.Urgency urgency,
                                          Instant waitlistedAt, boolean active, String center) {
        return registrationService.registerRecipient(new RegisterRecipientRequest(
                externalId, "受者-" + externalId, organ, blood, urgency, waitlistedAt,
                active, center));
    }
}
