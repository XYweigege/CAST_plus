package com.voc.insight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 单条文本即时分析请求 */
@Data
@Schema(description = "单条文本即时分析请求")
public class AnalyzeDTO {

    @Schema(description = "反馈正文", example = "理赔太慢了，提交两周还没动静", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 2000, message = "反馈内容最长 2000 字")
    private String content;

    @Schema(description = "产品线编码", example = "travel",
            allowableValues = {"travel", "medical", "accident", "home", "motor", "pet"})
    @Pattern(regexp = "^(travel|medical|accident|home|motor|pet)$", message = "产品线编码不合法")
    private String productLine;

    @Schema(description = "客户评分 1-5", example = "2", minimum = "1", maximum = "5")
    @Min(value = 1, message = "评分最小为 1")
    @Max(value = 5, message = "评分最大为 5")
    private Integer rating;

    @Schema(description = "语言", example = "zh-HK", allowableValues = {"zh-HK", "en", "mixed"})
    @Pattern(regexp = "^(zh-HK|en|mixed)$", message = "语言只支持 zh-HK / en / mixed")
    private String language;
}
