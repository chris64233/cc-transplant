package com.chris64233.cc.transplant.domain;

/**
 * 快照中候选人的当前状态。
 */
public enum CandidateState {
    /** 等待轮到邀约。 */
    WAITING,
    /** 当前持有有效邀约。 */
    OFFERED,
    /** 候选人拒绝邀约。 */
    DECLINED,
    /** 邀约过期。 */
    EXPIRED,
    /** 候选人在等待或邀约期间被停用，不再继续邀约。 */
    DEACTIVATED,
    /** 候选人接受邀约，成为最终受者。 */
    ACCEPTED
}
