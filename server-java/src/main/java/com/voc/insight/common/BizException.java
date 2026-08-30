package com.voc.insight.common;

import lombok.Getter;

/**
 * 业务异常。
 * 用于业务规则校验失败时抛出，由 GlobalExceptionHandler 统一捕获并转为 Result。
 * Service 层遇到业务校验失败直接抛，不用手写错误响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}
