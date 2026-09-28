package com.chris64233.cc.transplant.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chris64233.cc.transplant.service.BusinessException;
import com.chris64233.cc.transplant.service.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理，所有错误返回统一 {@link ErrorResponse} 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        ErrorResponse body = base(ex.getHttpStatus(), ex.getErrorCode().name(), ex.getMessage(),
                request.getRequestURI(), null);
        return org.springframework.http.ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex,
                                          HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(),
                        nullToEmpty(fe.getDefaultMessage()), fe.getRejectedValue()))
                .toList();
        return base(400, ErrorCode.VALIDATION_ERROR.name(), "请求参数校验失败",
                request.getRequestURI(), fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadable(HttpMessageNotReadableException ex,
                                          HttpServletRequest request) {
        return base(400, ErrorCode.VALIDATION_ERROR.name(), "请求体无法解析或字段取值非法",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex, HttpServletRequest request) {
        return base(500, "INTERNAL_ERROR", "服务内部错误: " + ex.getMessage(),
                request.getRequestURI(), null);
    }

    private ErrorResponse base(int status, String error, String message, String path,
                               List<ErrorResponse.FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
