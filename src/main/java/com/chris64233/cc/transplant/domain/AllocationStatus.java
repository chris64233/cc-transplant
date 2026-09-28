package com.chris64233.cc.transplant.domain;

/**
 * 分配流程状态。ALLOCATED / EXHAUSTED 均为终态。
 */
public enum AllocationStatus {
    /** 进行中：可能存在一份待响应邀约。 */
    IN_PROGRESS,
    /** 已分配：某候选人在邀约有效期内接受，器官进入终态。 */
    ALLOCATED,
    /** 候选名单遍历完毕，无人接受。 */
    EXHAUSTED
}
