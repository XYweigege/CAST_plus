package com.voc.insight.common;

import lombok.Getter;

/**
 * 响应状态码。
 * 业务错误码建议从 1000 开始，避免与 HTTP 状态码混淆。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),
    PARAM_ERROR(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    ERROR(500, "系统繁忙，请稍后重试"),

    // ---- 业务错误码 ----
    TOPIC_EXISTS(1001, "主题词已存在"),
    TOPIC_NOT_FOUND(1002, "主题词不存在"),
    FEEDBACK_NOT_FOUND(1003, "反馈不存在"),
    ALERT_NOT_FOUND(1004, "预警不存在"),
    CONTENT_EMPTY(1005, "反馈内容不能为空"),
    IMPORT_PARSE_FAIL(1006, "导入数据解析失败，未解析到有效反馈");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
