package com.voc.insight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 主题词创建 / 更新请求 */
@Data
@Schema(description = "主题词创建 / 更新请求")
public class TopicSaveDTO {

    @Schema(description = "主题词", example = "理赔时效", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "主题词不能为空")
    @Size(max = 50, message = "主题词最长 50 字")
    private String text;

    @Schema(description = "归属类别", example = "理赔")
    @Size(max = 50, message = "类别最长 50 字")
    private String category;

    @Schema(description = "是否启用，缺省为启用", example = "true")
    private Boolean isActive;
}
