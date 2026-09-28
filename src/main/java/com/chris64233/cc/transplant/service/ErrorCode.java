package com.chris64233.cc.transplant.service;

/**
 * 业务错误码，随 web 层统一错误结构返回给调用方。
 */
public enum ErrorCode {
    VALIDATION_ERROR,
    NOT_FOUND,
    /** 资源状态不允许该操作（如重复发起、对非当前邀约决定）。 */
    CONFLICT,
    /** 幂等标识被重复使用，但请求内容与首次不一致。 */
    IDEMPOTENCY_CONFLICT,
    /** 唯一约束冲突（外部编号重复等）。 */
    UNIQUE_CONSTRAINT,
    /** 无合格候选人，分配流程无法创建。 */
    NO_ELIGIBLE_CANDIDATE,
    /** 邀约已过期或尚未过期等时间相关冲突。 */
    OFFER_EXPIRED,
    OFFER_NOT_EXPIRED,
    /** 候选人已停用，不能接受。 */
    RECIPIENT_INACTIVE,
    /** 流程已结束（已分配或遍历完毕）。 */
    ALLOCATION_FINISHED
}
