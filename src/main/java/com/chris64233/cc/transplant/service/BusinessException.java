package com.chris64233.cc.transplant.service;


/**
 * 业务异常，携带错误码与 HTTP 状态，由全局异常处理器转换为统一错误结构。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;

    public BusinessException(ErrorCode errorCode, int httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, 404, message);
    }

    public static BusinessException conflict(ErrorCode code, String message) {
        return new BusinessException(code, 409, message);
    }

    public static BusinessException unprocessable(ErrorCode code, String message) {
        return new BusinessException(code, 422, message);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
