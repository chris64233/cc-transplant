package com.chris64233.cc.transplant.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.transplant.dto.CandidateResponse;
import com.chris64233.cc.transplant.dto.DonorResponse;
import com.chris64233.cc.transplant.dto.RegisterCandidateRequest;
import com.chris64233.cc.transplant.dto.RegisterDonorRequest;
import com.chris64233.cc.transplant.service.RegistrationService;

import jakarta.validation.Valid;

/**
 * 供体与候选人登记接口。
 */
@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/donors")
    public ResponseEntity<DonorResponse> registerDonor(@Valid @RequestBody RegisterDonorRequest request) {
        DonorResponse response = registrationService.registerDonor(request);
        return ResponseEntity.created(URI.create("/api/registrations/donors/" + response.id())).body(response);
    }

    @PostMapping("/candidates")
    public ResponseEntity<CandidateResponse> registerCandidate(
            @Valid @RequestBody RegisterCandidateRequest request) {
        CandidateResponse response = registrationService.registerCandidate(request);
        return ResponseEntity.created(URI.create("/api/registrations/candidates/" + response.id()))
                .body(response);
    }
}
