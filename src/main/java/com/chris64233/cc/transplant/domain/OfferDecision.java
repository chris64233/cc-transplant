package com.chris64233.cc.transplant.domain;

/**
 * 候选人针对邀约作出的决定类型，同时作为幂等事件的类型。
 */
public enum OfferDecision {
    ACCEPT,
    DECLINE,
    EXPIRE
}
