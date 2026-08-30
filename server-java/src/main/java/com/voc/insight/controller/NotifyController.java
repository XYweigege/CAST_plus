package com.voc.insight.controller;

import com.voc.insight.common.Result;
import com.voc.insight.service.InsightService;
import com.voc.insight.service.NotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 实时通知与系统接口。
 */
@Tag(name = "系统与实时通知")
@RestController
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;
    private final InsightService insightService;

    @Operation(summary = "SSE 实时推送通道")
    @GetMapping("/api/notify/stream")
    public SseEmitter stream() {
        return notifyService.register();
    }

    @Operation(summary = "健康检查")
    @GetMapping("/api/health")
    public Result<Map<String, String>> health() {
        return Result.success(Map.of(
                "status", "ok",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @Operation(summary = "手动触发一轮分析")
    @PostMapping("/api/check-feedbacks")
    public Result<Map<String, Object>> check() {
        int created = insightService.runCheck();
        return Result.success(Map.of("message", "Insight check completed", "created", created));
    }
}
