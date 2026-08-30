package com.voc.insight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 人工复核请求 */
@Data
@Schema(description = "人工复核请求（仅提交需要修正的字段）")
public class ReviewDTO {

    @Schema(description = "修正后的情感倾向", example = "negative",
            allowableValues = {"positive", "neutral", "negative"})
    @Pattern(regexp = "^(positive|neutral|negative)$", message = "情感倾向不合法")
    private String sentiment;

    @Schema(description = "修正后的主题标签列表，需属于业务字典 TOPIC_TAGS",
            example = "[\"理赔时效\", \"客服响应\"]")
    @Size(max = 10, message = "主题标签最多 10 个")
    private List<@Size(max = 50, message = "单个主题标签最长 50 字") String> topics;

    @Schema(description = "修正后的紧急度", example = "action",
            allowableValues = {"info", "attention", "action", "critical"})
    @Pattern(regexp = "^(info|attention|action|critical)$", message = "紧急度不合法")
    private String urgency;
}
