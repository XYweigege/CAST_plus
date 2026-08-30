package com.voc.insight.controller;

import com.voc.insight.ai.dto.FeedbackAnalysis;
import com.voc.insight.common.BizException;
import com.voc.insight.common.PageResult;
import com.voc.insight.common.Result;
import com.voc.insight.common.ResultCode;
import com.voc.insight.dto.AnalyzeDTO;
import com.voc.insight.dto.FeedbackQueryDTO;
import com.voc.insight.dto.ImportDTO;
import com.voc.insight.dto.ReviewDTO;
import com.voc.insight.entity.Feedback;
import com.voc.insight.service.FeedbackService;
import com.voc.insight.vo.FeedbackStatsVO;
import com.voc.insight.vo.InsightReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 客户反馈接口。
 */
@Tag(name = "客户反馈")
@Validated
@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "反馈列表（多维筛选 + 排序 + 分页）")
    @GetMapping
    public Result<PageResult<Feedback>> page(@Valid @ModelAttribute FeedbackQueryDTO query) {
        return Result.success(feedbackService.page(query));
    }

    @Operation(summary = "概览统计")
    @GetMapping("/stats")
    public Result<FeedbackStatsVO> stats() {
        return Result.success(feedbackService.stats());
    }

    @Operation(summary = "评分归因报告")
    @GetMapping("/insight")
    public Result<InsightReport> insight(
            @Parameter(description = "产品线编码，不传则统计全部", example = "travel")
            @RequestParam(required = false)
            @Pattern(regexp = "^(travel|medical|accident|home|motor|pet)$", message = "产品线编码不合法")
            String productLine) {
        return Result.success(feedbackService.insight(productLine));
    }

    @Operation(summary = "反馈详情")
    @GetMapping("/{id}")
    public Result<Feedback> get(
            @Parameter(description = "反馈 ID") @PathVariable @NotBlank(message = "反馈 ID 不能为空") String id) {
        return Result.success(feedbackService.getById(id));
    }

    @Operation(summary = "单条文本即时分析（不落库）")
    @PostMapping("/analyze")
    public Result<FeedbackAnalysis> analyze(@Valid @RequestBody AnalyzeDTO dto) {
        return Result.success(feedbackService.analyze(dto));
    }

    @Operation(summary = "批量导入（CSV / JSON）")
    @PostMapping("/import")
    public Result<Map<String, Integer>> importData(@Valid @RequestBody ImportDTO dto) {
        int created = feedbackService.importData(dto.getContent(), dto.getFormat());
        return Result.success(Map.of("created", created));
    }

    @Operation(summary = "生成演示数据")
    @PostMapping("/generate-demo")
    public Result<Map<String, Integer>> generateDemo(@RequestBody(required = false) Map<String, Integer> body) {
        Integer count = body == null ? null : body.get("count");
        if (count != null && (count < 1 || count > 500)) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        int created = feedbackService.generateDemo(count);
        return Result.success(Map.of("created", created));
    }

    @Operation(summary = "人工复核")
    @PatchMapping("/{id}/review")
    public Result<Feedback> review(
            @Parameter(description = "反馈 ID") @PathVariable @NotBlank(message = "反馈 ID 不能为空") String id,
            @Valid @RequestBody ReviewDTO dto) {
        return Result.success(feedbackService.review(id, dto));
    }

    @Operation(summary = "删除反馈")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "反馈 ID") @PathVariable @NotBlank(message = "反馈 ID 不能为空") String id) {
        feedbackService.removeById(id);
        return Result.success();
    }
}
