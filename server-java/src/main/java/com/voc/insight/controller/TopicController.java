package com.voc.insight.controller;

import com.voc.insight.common.BizException;
import com.voc.insight.common.Result;
import com.voc.insight.common.ResultCode;
import com.voc.insight.dto.TopicSaveDTO;
import com.voc.insight.entity.Topic;
import com.voc.insight.service.TopicService;
import com.voc.insight.vo.TopicExpandVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 主题词管理接口。
 */
@Tag(name = "主题词管理")
@Validated
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @Operation(summary = "主题词列表")
    @GetMapping
    public Result<List<Topic>> list() {
        return Result.success(topicService.listAll());
    }

    @Operation(summary = "主题词详情")
    @GetMapping("/{id}")
    public Result<Topic> get(
            @Parameter(description = "主题词 ID") @PathVariable @NotBlank(message = "主题词 ID 不能为空") String id) {
        return Result.success(topicService.getById(id));
    }

    @Operation(summary = "创建主题词")
    @PostMapping
    public Result<Topic> create(@Valid @RequestBody TopicSaveDTO dto) {
        return Result.success(topicService.createTopic(dto));
    }

    @Operation(summary = "更新主题词")
    @PutMapping("/{id}")
    public Result<Topic> update(
            @Parameter(description = "主题词 ID") @PathVariable @NotBlank(message = "主题词 ID 不能为空") String id,
            @Valid @RequestBody TopicSaveDTO dto) {
        return Result.success(topicService.updateTopic(id, dto));
    }

    @Operation(summary = "删除主题词")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "主题词 ID") @PathVariable @NotBlank(message = "主题词 ID 不能为空") String id) {
        topicService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "启停切换")
    @PatchMapping("/{id}/toggle")
    public Result<Topic> toggle(
            @Parameter(description = "主题词 ID") @PathVariable @NotBlank(message = "主题词 ID 不能为空") String id) {
        return Result.success(topicService.toggle(id));
    }

    @Operation(summary = "AI 扩展口语表达变体")
    @PostMapping("/{id}/expand")
    public Result<TopicExpandVO> expand(
            @Parameter(description = "主题词 ID") @PathVariable @NotBlank(message = "主题词 ID 不能为空") String id) {
        return Result.success(topicService.expand(id));
    }

    @Operation(summary = "人工确认 / 否决变体")
    @PatchMapping("/{id}/approve")
    public Result<Topic> approve(
            @Parameter(description = "主题词 ID") @PathVariable @NotBlank(message = "主题词 ID 不能为空") String id,
            @Parameter(description = "请求体：{\"approved\": true/false}")
            @RequestBody Map<String, Boolean> body) {
        Boolean approved = body.get("approved");
        if (approved == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        return Result.success(topicService.approve(id, approved));
    }
}
