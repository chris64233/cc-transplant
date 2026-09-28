package com.chris64233.cc.transplant.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.transplant.dto.AllocationDetailResponse;
import com.chris64233.cc.transplant.dto.AllocationStartedResponse;
import com.chris64233.cc.transplant.dto.DecisionRequest;
import com.chris64233.cc.transplant.dto.DecisionResponse;
import com.chris64233.cc.transplant.dto.StartAllocationRequest;
import com.chris64233.cc.transplant.error.BusinessException;
import com.chris64233.cc.transplant.error.ErrorCode;
import com.chris64233.cc.transplant.service.AllocationService;

import jakarta.validation.Valid;

/**
 * 分配流程接口：启动、接受/拒绝/过期决定、详情查询。
 */
@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping
    public ResponseEntity<AllocationStartedResponse> start(@Valid @RequestBody StartAllocationRequest body) {
        AllocationStartedResponse response =
                allocationService.start(body.donorExternalRef(), body.offerTtlSeconds());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{allocationNo}")
    public AllocationDetailResponse detail(@PathVariable String allocationNo) {
        return allocationService.getDetail(allocationNo);
    }

    @PostMapping("/offers/{offerNo}/accept")
    public DecisionResponse accept(@PathVariable String offerNo,
                                   @Valid @RequestBody DecisionRequest request) {
        verifyTarget(offerNo, request);
        return allocationService.accept(offerNo, request.eventId());
    }

    @PostMapping("/offers/{offerNo}/decline")
    public DecisionResponse decline(@PathVariable String offerNo,
                                    @Valid @RequestBody DecisionRequest request) {
        verifyTarget(offerNo, request);
        return allocationService.decline(offerNo, request.eventId());
    }

    @PostMapping("/offers/{offerNo}/expire")
    public DecisionResponse expire(@PathVariable String offerNo,
                                   @Valid @RequestBody DecisionRequest request) {
        verifyTarget(offerNo, request);
        return allocationService.expire(offerNo, request.eventId());
    }

    private static void verifyTarget(String pathOfferNo, DecisionRequest request) {
        if (!pathOfferNo.equals(request.offerNo())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "路径中的邀约编号与请求体不一致");
        }
    }
}
