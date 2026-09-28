package com.chris64233.cc.transplant.domain;

/**
 * 分配流程状态。
 */
public enum AllocationStatus {
    /** 分配进行中，正在按候选顺序逐一发出限时邀约。 */
    IN_PROGRESS,
    /** 已有候选人在有效期内接受，器官进入终态。 */
    ALLOCATED,
    /** 所有候选人都已拒绝或邀约过期，无剩余候选人。 */
    EXHAUSTED
}
