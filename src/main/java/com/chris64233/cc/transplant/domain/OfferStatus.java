package com.chris64233.cc.transplant.domain;

/**
 * 邀约状态。
 */
public enum OfferStatus {
    /** 邀约有效，可在过期前接受或拒绝。 */
    PENDING,
    /** 候选人接受。 */
    ACCEPTED,
    /** 候选人拒绝。 */
    DECLINED,
    /** 已过期。 */
    EXPIRED
}
