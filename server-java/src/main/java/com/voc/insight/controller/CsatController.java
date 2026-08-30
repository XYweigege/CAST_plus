package com.voc.insight.controller;

import com.voc.insight.common.Result;
import com.voc.insight.dto.FeedbackInput;
import com.voc.insight.entity.Feedback;
import com.voc.insight.service.FeedbackProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CSAT 采集接口。
 * 外部产品（问卷/理赔/客服系统）调用本接口推送客户反馈，
 * 数据以 raw 状态落库（is_analyzed=0），由定时任务扫描归因。
 */
@Tag(name = "CSAT 采集", description = "外部产品推送客户反馈入口")
@RestController
@RequestMapping("/api/csat")
@RequiredArgsConstructor
public class CsatController {

    private final FeedbackProcessService processService;

    /**
     * 接收一条客户反馈并落库（未归因）。
     * 重复的 source+sourceId 会被去重跳过。
     */
    @Operation(summary = "推送一条客户反馈（raw 落库，等待定时归因）")
    @PostMapping("/ingest")
    public Result<Map<String, Object>> ingest(@Valid @RequestBody FeedbackInput input) {
        Feedback saved = processService.ingestRaw(input);
        if (saved == null) {
            return Result.success(Map.of("created", 0, "reason", "duplicate"));
        }
        return Result.success(Map.of("created", 1, "id", saved.getId()));
    }
}
