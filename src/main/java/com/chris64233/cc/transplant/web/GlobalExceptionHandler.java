package com.chris64233.cc.transplant.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.chris64233.cc.transplant.error.BusinessException;
import com.chris64233.cc.transplant.error.ErrorCode;

/**
 * 全局异常处理：所有错误返回统一 {@link ErrorResponse} 结构。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(ex.getCode(), ex.getMessage(), request, ex.getCode().httpStatus(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<ErrorResponse.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldErrorDetail(fe.getField(),
                        fe.getDefaultMessage() == null ? "无效值" : fe.getDefaultMessage()))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "请求参数校验失败", request, HttpStatus.BAD_REQUEST,
                details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_ERROR, "请求体无法解析或字段类型错误", request,
                HttpStatus.BAD_REQUEST, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.INTERNAL_ERROR, "服务内部错误", request,
                HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, HttpServletRequest request,
                                                HttpStatus status,
                                                List<ErrorResponse.FieldErrorDetail> details) {
        ErrorResponse body = new ErrorResponse(code.name(), message, request.getRequestURI(),
                Instant.now(clock), details);
        return ResponseEntity.status(status).body(body);
    }
}
