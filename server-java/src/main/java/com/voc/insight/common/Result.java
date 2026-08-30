package com.voc.insight.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应封装。
 * 所有接口返回固定结构，前端只需判断 code 是否为 0，
 * 统一在 request 层解包 data，不必逐个接口处理。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 状态码：0 成功，非 0 失败（见 ResultCode） */
    @Schema(description = "状态码：0 成功，非 0 失败", example = "0")
    private Integer code;

    /** 提示信息 */
    @Schema(description = "提示信息", example = "成功")
    private String message;

    /** 业务数据 */
    @Schema(description = "业务数据")
    private T data;

    /** 响应时间戳 */
    @Schema(description = "响应时间戳（毫秒）", example = "1756564800000")
    private Long timestamp = System.currentTimeMillis();

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(ResultCode.ERROR.getCode(), message);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
