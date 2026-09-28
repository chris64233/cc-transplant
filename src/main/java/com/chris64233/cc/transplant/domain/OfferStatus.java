package com.chris64233.cc.transplant.domain;

/**
 * 邀约状态。
 */
public enum OfferStatus {
    /** 已发出、尚未被决定，且仍在有效期内（或尚未进行过期判定）。 */
    PENDING,
    /** 候选人在有效期内接受。 */
    ACCEPTED,
    /** 候选人拒绝。 */
    DECLINED,
    /** 超过过期时间仍未决定。 */
    EXPIRED
}
