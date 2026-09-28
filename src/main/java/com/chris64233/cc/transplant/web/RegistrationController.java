package com.chris64233.cc.transplant.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.transplant.service.RegistrationService;
import com.chris64233.cc.transplant.web.dto.DonorResponse;
import com.chris64233.cc.transplant.web.dto.RecipientResponse;
import com.chris64233.cc.transplant.web.dto.RegisterDonorRequest;
import com.chris64233.cc.transplant.web.dto.RegisterRecipientRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/donors")
    @ResponseStatus(HttpStatus.CREATED)
    public DonorResponse registerDonor(@Valid @RequestBody RegisterDonorRequest request) {
        return DonorResponse.from(registrationService.registerDonor(request));
    }

    @PostMapping("/recipients")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipientResponse registerRecipient(@Valid @RequestBody RegisterRecipientRequest request) {
        return RecipientResponse.from(registrationService.registerRecipient(request));
    }
}
