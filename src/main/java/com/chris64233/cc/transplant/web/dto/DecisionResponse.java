package com.chris64233.cc.transplant.web.dto;

/**
 * 决定接口响应。replayed=true 表示本次为幂等重放，返回的是首次处理的原结果。
 */
public record DecisionResponse(
        String eventId,
        boolean replayed,
        OfferResponse offer) {
}
