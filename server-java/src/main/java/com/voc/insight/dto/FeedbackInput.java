package com.voc.insight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待分析的反馈输入对象。
 * 来源：定时任务采集 / 文件导入 / 单条即时分析。
 */
@Data
@Schema(description = "待分析的反馈输入对象")
public class FeedbackInput {

    @Schema(description = "反馈正文", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 5000, message = "反馈内容最长 5000 字")
    private String content;

    @Schema(description = "标题（问卷/工单可能有）")
    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    /** 渠道编码 */
    @Schema(description = "渠道编码", example = "survey", defaultValue = "survey",
            allowableValues = {"survey", "claim", "service", "social", "appstore", "email"})
    @Pattern(regexp = "^(survey|claim|service|social|appstore|email)$", message = "渠道编码不合法")
    private String source = "survey";

    /** 业务系统内唯一 ID */
    @Schema(description = "业务系统内唯一 ID，用于去重")
    @Size(max = 100, message = "sourceId 最长 100 字")
    private String sourceId;

    @Schema(description = "原文链接")
    @Size(max = 500, message = "链接最长 500 字")
    private String url;

    @Schema(description = "客户评分 1-5", example = "2", minimum = "1", maximum = "5")
    @Min(value = 1, message = "评分最小为 1")
    @Max(value = 5, message = "评分最大为 5")
    private Integer rating;

    @Schema(description = "产品线编码", example = "travel",
            allowableValues = {"travel", "medical", "accident", "home", "motor", "pet"})
    @Pattern(regexp = "^(travel|medical|accident|home|motor|pet)$", message = "产品线编码不合法")
    private String productLine;

    @Schema(description = "语言", example = "zh-HK", allowableValues = {"zh-HK", "en", "mixed"})
    @Pattern(regexp = "^(zh-HK|en|mixed)$", message = "语言只支持 zh-HK / en / mixed")
    private String language;

    @Schema(description = "客户名（应脱敏）")
    @Size(max = 100, message = "客户名最长 100 字")
    private String authorName;

    @Schema(description = "反馈发生时间", example = "2026-08-30 12:00:00")
    private LocalDateTime publishedAt;
}
