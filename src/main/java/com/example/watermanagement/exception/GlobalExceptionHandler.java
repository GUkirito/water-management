package com.example.watermanagement.exception;

import com.example.watermanagement.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.format.DateTimeParseException;

/**
 * 全局异常处理器
 * <p>
 * 拦截所有 Controller 抛出的异常，统一返回 ApiResponse 格式。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid 触发） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleUnreadableMessage(HttpMessageNotReadableException e) {
        log.warn("请求参数格式错误: {}", e.getMessage());
        return ApiResponse.fail("请求参数格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        return ApiResponse.fail("缺少必填参数：" + e.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        log.warn("缺少请求部分: {}", e.getMessage());
        return ApiResponse.fail(400, "缺少必填的上传文件或参数");
    }

    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(TypeMismatchException e) {
        return ApiResponse.fail("参数类型错误");
    }

    @ExceptionHandler(DateTimeParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleDateTimeParseException(DateTimeParseException e) {
        log.warn("日期格式错误: {}", e.getMessage());
        return ApiResponse.fail(400, "日期格式错误，请使用 yyyy-MM-dd 格式");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.fail(405, "不支持的请求方法");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiResponse<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ApiResponse.fail(415, "不支持的 Content-Type");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return ApiResponse.fail(message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("数据冲突: {}", e.getMessage());
        return ApiResponse.fail("数据冲突，请检查是否存在重复数据");
    }

    /** 静态资源不存在（如 favicon.ico），返回 404，不打 ERROR 日志 */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        return ApiResponse.fail(404, "资源不存在: " + e.getResourcePath());
    }

    /** 其他未处理的异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("服务器内部错误: {} {}, 异常: {}", request.getMethod(), request.getRequestURI(), e.getMessage(), e);
        return ApiResponse.fail(500, "服务器内部错误，请稍后重试");
    }
}
