package com.voc.insight.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * 集中把各类异常转为统一 Result，避免每个 Controller 都写 try/catch。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /** 参数校验失败（@Valid @RequestBody） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 参数绑定失败（@Valid @ModelAttribute / 表单） */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 方法参数校验失败（@Validated + @RequestParam / @PathVariable 约束，AOP 拦截） */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("；"));
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 方法参数校验失败（Spring MVC 6.1 内置方法校验） */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleMethodValidation(HandlerMethodValidationException e) {
        String msg = e.getAllValidationResults().stream()
                .map(ParameterValidationResult::getResolvableErrors)
                .flatMap(java.util.List::stream)
                .map(MessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return Result.error(ResultCode.PARAM_ERROR.getCode(), msg);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "缺少必填参数: " + e.getParameterName());
    }

    /** 参数类型不匹配（如 page=abc） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "参数类型错误: " + e.getName());
    }

    /** 请求体 JSON 解析失败 / 缺失 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(ResultCode.PARAM_ERROR.getCode(), "请求体格式错误或缺失");
    }

    /** 接口不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.error(ResultCode.NOT_FOUND.getCode(), "接口不存在: " + e.getResourcePath());
    }

    /** 兜底：未预期的系统异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMessage());
    }
}
