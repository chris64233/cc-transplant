package com.chris64233.cc.transplant.error;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。code 为稳定字符串，便于客户端程序化处理；httpStatus 为对应 HTTP 状态。
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 外部编号重复。 */
    DUPLICATE_EXTERNAL_REF(HttpStatus.CONFLICT),
    /** 幂等事件冲突：相同事件 ID 但内容不一致，或并发决定与已生效结果冲突。 */
    EVENT_CONFLICT(HttpStatus.CONFLICT),
    /** 业务规则不满足：无合格候选人、邀约过期/已失效、候选人停用、器官已分配等。 */
    RULE_VIOLATION(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
