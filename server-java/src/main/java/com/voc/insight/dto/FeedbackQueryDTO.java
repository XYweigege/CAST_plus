package com.voc.insight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 反馈列表查询参数（@ModelAttribute 绑定 query string） */
@Data
@Schema(description = "反馈列表查询参数")
public class FeedbackQueryDTO {

    @Schema(description = "页码，从 1 开始", example = "1", defaultValue = "1")
    @Min(value = 1, message = "页码最小为 1")
    private Integer page = 1;

    @Schema(description = "每页条数，最大 200", example = "20", defaultValue = "20")
    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 200, message = "每页条数最大为 200")
    private Integer limit = 20;

    @Schema(description = "渠道编码", example = "survey",
            allowableValues = {"survey", "claim", "service", "social", "appstore", "email"})
    @Pattern(regexp = "^(survey|claim|service|social|appstore|email)$", message = "渠道编码不合法")
    private String source;

    @Schema(description = "情感倾向", example = "negative",
            allowableValues = {"positive", "neutral", "negative"})
    @Pattern(regexp = "^(positive|neutral|negative)$", message = "情感倾向不合法")
    private String sentiment;

    @Schema(description = "紧急度", example = "critical",
            allowableValues = {"info", "attention", "action", "critical"})
    @Pattern(regexp = "^(info|attention|action|critical)$", message = "紧急度不合法")
    private String urgency;

    @Schema(description = "产品线编码", example = "travel",
            allowableValues = {"travel", "medical", "accident", "home", "motor", "pet"})
    @Pattern(regexp = "^(travel|medical|accident|home|motor|pet)$", message = "产品线编码不合法")
    private String productLine;

    @Schema(description = "归属主题词 ID")
    private String topicId;

    /** 关键词：匹配反馈内容或 AI 归因 */
    @Schema(description = "关键词：匹配反馈内容或 AI 归因")
    @Size(max = 100, message = "关键词最长 100 字")
    private String keyword;

    /** "true" 表示仅待复核 */
    @Schema(description = "传 true 表示仅看待复核", example = "true", allowableValues = {"true", "false"})
    @Pattern(regexp = "^(true|false)$", message = "pendingReview 只能为 true 或 false")
    private String pendingReview;

    /** 24h / today / 7d / 30d */
    @Schema(description = "时间范围", example = "7d", allowableValues = {"24h", "today", "7d", "30d"})
    @Pattern(regexp = "^(24h|today|7d|30d)$", message = "时间范围只支持 24h / today / 7d / 30d")
    private String timeRange;

    @Schema(description = "排序字段", example = "createdAt", defaultValue = "createdAt",
            allowableValues = {"createdAt", "rating", "confidence", "publishedAt", "urgency"})
    @Pattern(regexp = "^(createdAt|rating|confidence|publishedAt|urgency)$", message = "排序字段不合法")
    private String sortBy = "createdAt";

    @Schema(description = "排序方向", example = "desc", defaultValue = "desc",
            allowableValues = {"asc", "desc"})
    @Pattern(regexp = "^(asc|desc)$", message = "排序方向只能为 asc 或 desc")
    private String sortOrder = "desc";
}
