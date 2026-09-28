package com.chris64233.cc.transplant.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.cc.transplant.service.AllocationService;
import com.chris64233.cc.transplant.service.AllocationService.DecisionOutcome;
import com.chris64233.cc.transplant.web.dto.AllocationDetailResponse;
import com.chris64233.cc.transplant.web.dto.DecisionRequest;
import com.chris64233.cc.transplant.web.dto.DecisionResponse;
import com.chris64233.cc.transplant.web.dto.ExpireRequest;
import com.chris64233.cc.transplant.web.dto.OfferResponse;
import com.chris64233.cc.transplant.web.dto.StartAllocationRequest;

import jakarta.validation.Valid;

/**
 * 分配流程接口：
 * <ul>
 *   <li>POST /api/allocations 启动分配并固化候选快照</li>
 *   <li>GET  /api/allocations/{allocationNo} 查询分配详情</li>
 *   <li>POST /api/allocations/{allocationNo}/offers 向当前候选人发出限时邀约</li>
 *   <li>POST .../offers/{offerNo}/accept|decline|expire 携带幂等事件标识做决定</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationService allocationService;

    public AllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AllocationDetailResponse start(@Valid @RequestBody StartAllocationRequest request) {
        return allocationService.startAllocation(request);
    }

    @GetMapping("/{allocationNo}")
    public AllocationDetailResponse detail(@PathVariable String allocationNo) {
        return allocationService.getDetail(allocationNo);
    }

    @PostMapping("/{allocationNo}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse issueOffer(@PathVariable String allocationNo) {
        return allocationService.issueOffer(allocationNo);
    }

    @PostMapping("/{allocationNo}/offers/{offerNo}/accept")
    public DecisionResponse accept(@PathVariable String allocationNo,
                                   @PathVariable String offerNo,
                                   @Valid @RequestBody DecisionRequest request) {
        return toResponse(request.eventId(),
                allocationService.accept(allocationNo, offerNo, request.eventId()));
    }

    @PostMapping("/{allocationNo}/offers/{offerNo}/decline")
    public DecisionResponse decline(@PathVariable String allocationNo,
                                    @PathVariable String offerNo,
                                    @Valid @RequestBody DecisionRequest request) {
        return toResponse(request.eventId(),
                allocationService.decline(allocationNo, offerNo, request.eventId()));
    }

    @PostMapping("/{allocationNo}/offers/{offerNo}/expire")
    public DecisionResponse expire(@PathVariable String allocationNo,
                                   @PathVariable String offerNo,
                                   @Valid @RequestBody ExpireRequest request) {
        return toResponse(request.eventId(),
                allocationService.expire(allocationNo, offerNo, request.eventId()));
    }

    private DecisionResponse toResponse(String eventId, DecisionOutcome outcome) {
        return new DecisionResponse(eventId, outcome.replayed(), outcome.offer());
    }
}
