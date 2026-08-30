package com.voc.insight.controller;

import com.voc.insight.common.PageResult;
import com.voc.insight.common.Result;
import com.voc.insight.entity.Alert;
import com.voc.insight.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 预警中心接口。
 */
@Tag(name = "预警中心")
@Validated
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "预警列表")
    @GetMapping
    public Result<Map<String, Object>> list(
            @Parameter(description = "页码，从 1 开始", example = "1")
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为 1") Integer page,
            @Parameter(description = "每页条数，最大 200", example = "50")
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "每页条数最小为 1")
            @Max(value = 200, message = "每页条数最大为 200") Integer limit,
            @Parameter(description = "传 true 仅看未读", example = "true")
            @RequestParam(required = false)
            @Pattern(regexp = "^(true|false)$", message = "unreadOnly 只能为 true 或 false") String unreadOnly,
            @Parameter(description = "传 true 仅看未处置", example = "true")
            @RequestParam(required = false)
            @Pattern(regexp = "^(true|false)$", message = "unhandledOnly 只能为 true 或 false") String unhandledOnly) {
        PageResult<Alert> p = alertService.page(page, limit,
                "true".equals(unreadOnly), "true".equals(unhandledOnly));
        return Result.success(Map.of(
                "data", p.getRecords(),
                "unreadCount", alertService.unreadCount(),
                "unhandledCount", alertService.unhandledCount(),
                "pagination", p
        ));
    }

    @Operation(summary = "标记已读")
    @PatchMapping("/{id}/read")
    public Result<Alert> markRead(
            @Parameter(description = "预警 ID") @PathVariable @NotBlank(message = "预警 ID 不能为空") String id) {
        return Result.success(alertService.markRead(id));
    }

    @Operation(summary = "全部已读")
    @PatchMapping("/read-all")
    public Result<Map<String, String>> markAllRead() {
        alertService.markAllRead();
        return Result.success(Map.of("message", "All alerts marked as read"));
    }

    @Operation(summary = "标记业务已处置")
    @PatchMapping("/{id}/handle")
    public Result<Alert> handle(
            @Parameter(description = "预警 ID") @PathVariable @NotBlank(message = "预警 ID 不能为空") String id) {
        return Result.success(alertService.handle(id));
    }

    @Operation(summary = "删除预警")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "预警 ID") @PathVariable @NotBlank(message = "预警 ID 不能为空") String id) {
        alertService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "清空预警")
    @DeleteMapping
    public Result<Map<String, String>> clear() {
        alertService.clear();
        return Result.success(Map.of("message", "All alerts deleted"));
    }
}
