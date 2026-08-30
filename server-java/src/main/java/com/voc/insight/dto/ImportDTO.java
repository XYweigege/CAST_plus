package com.voc.insight.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 批量导入请求 */
@Data
@Schema(description = "批量导入请求")
public class ImportDTO {

    @Schema(description = "导入内容（JSON 数组或 CSV 文本）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "导入内容不能为空")
    @Size(max = 1_000_000, message = "导入内容超过大小限制（1MB）")
    private String content;

    /** json / csv */
    @Schema(description = "数据格式", example = "json", defaultValue = "json", allowableValues = {"json", "csv"})
    @Pattern(regexp = "^(json|csv)$", message = "数据格式只支持 json 或 csv")
    private String format = "json";
}
