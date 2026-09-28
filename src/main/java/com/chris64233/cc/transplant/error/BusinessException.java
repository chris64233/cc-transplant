package com.chris64233.cc.transplant.error;

/**
 * 服务层业务异常基类，由全局异常处理器统一转换为错误响应体。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
