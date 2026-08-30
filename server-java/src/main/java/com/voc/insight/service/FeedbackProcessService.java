package com.voc.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voc.insight.ai.dto.FeedbackAnalysis;
import com.voc.insight.constant.BusinessDict;
import com.voc.insight.dto.FeedbackInput;
import com.voc.insight.entity.Alert;
import com.voc.insight.entity.Feedback;
import com.voc.insight.mapper.AlertMapper;
import com.voc.insight.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 反馈处理组件。
 * 统一「保存 → 实时推送 → 预警」三步，供定时任务、文件导入、演示生成三处复用，
 * 避免同样的逻辑在三处各写一遍。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackProcessService {

    /** 去重 SET key 前缀：按渠道分桶，member 为 sourceId */
    private static final String DEDUP_KEY = "voc:dedup:";

    private final FeedbackMapper feedbackMapper;
    private final AlertMapper alertMapper;
    private final NotifyService notifyService;
    private final MailService mailService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 保存一条已分析的反馈，并按紧急度决定是否预警。
     *
     * @param input    原始反馈
     * @param analysis AI 分析结果
     * @param topicId  归属主题词（无归属可为 null）
     * @return 保存后的反馈；重复数据返回 null
     */
    public Feedback saveFeedback(FeedbackInput input, FeedbackAnalysis analysis, String topicId) {
        // 去重：同一来源的同一条记录只处理一次（Redis 快速路径，DB 唯一约束兜底）
        if (isDuplicate(input)) {
            return null;
        }

        Feedback feedback = new Feedback();
        feedback.setTitle(input.getTitle());
        feedback.setContent(input.getContent());
        feedback.setSource(input.getSource());
        feedback.setSourceId(input.getSourceId());
        feedback.setUrl(input.getUrl());
        feedback.setRating(input.getRating());
        feedback.setProductLine(input.getProductLine());
        feedback.setLanguage(input.getLanguage());
        feedback.setAuthorName(input.getAuthorName());
        feedback.setPublishedAt(input.getPublishedAt());
        feedback.setSentiment(analysis.getSentiment());
        feedback.setTopics(toJson(analysis));
        feedback.setUrgency(analysis.getUrgency());
        feedback.setUrgencyReason(analysis.getUrgencyReason());
        feedback.setAiSummary(analysis.getAiSummary());
        feedback.setConfidence(BigDecimal.valueOf(analysis.getConfidence()));
        // 低置信度判定不写入终态，等待人工复核
        feedback.setIsReviewed(analysis.getConfidence() >= BusinessDict.CONFIDENCE_THRESHOLD);
        feedback.setTopicId(topicId);

        try {
            feedbackMapper.insert(feedback);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底，并发或重复时跳过
            return null;
        }

        // 实时推送
        notifyService.push("feedback:new", feedback);

        // 预警：仅 action / critical 触发，避免告警疲劳
        if (BusinessDict.ALERT_URGENCIES.contains(analysis.getUrgency())) {
            createAlert(feedback, analysis);
        }

        return feedback;
    }

    /**
     * 判重：优先 Redis SADD（O(1)，不打数据库）；Redis 不可用时回退 DB 查询。
     * sourceId 为空时不判重（与原 SQL 语义一致，NULL 不参与去重）。
     */
    private boolean isDuplicate(FeedbackInput input) {
        if (!StringUtils.hasText(input.getSourceId())) {
            return false;
        }
        try {
            Long added = redisTemplate.opsForSet()
                    .add(DEDUP_KEY + input.getSource(), input.getSourceId());
            return added != null && added == 0;
        } catch (Exception e) {
            log.warn("Redis 去重不可用，回退数据库查询: {}", e.getMessage());
            Long exists = feedbackMapper.selectCount(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getSource, input.getSource())
                    .eq(Feedback::getSourceId, input.getSourceId()));
            return exists > 0;
        }
    }

    private void createAlert(Feedback feedback, FeedbackAnalysis analysis) {
        Alert alert = new Alert();
        alert.setType("negative");
        alert.setTitle(("critical".equals(analysis.getUrgency()) ? "紧急" : "待处理") + "："
                + (analysis.getTopics().isEmpty() ? "客户反馈" : analysis.getTopics().get(0)));
        alert.setContent(truncate(
                (feedback.getAiSummary() != null ? feedback.getAiSummary() : feedback.getContent()), 80)
                + "（产品线：" + (feedback.getProductLine() == null ? "未知" : feedback.getProductLine()) + "）");
        alert.setUrgency(analysis.getUrgency());
        alert.setFeedbackId(feedback.getId());
        alertMapper.insert(alert);

        notifyService.push("alert", Map.of(
                "id", alert.getId(),
                "title", alert.getTitle(),
                "content", alert.getContent(),
                "urgency", alert.getUrgency(),
                "feedbackId", feedback.getId()
        ));

        mailService.sendAlert(alert.getTitle(), alert.getContent(), alert.getUrgency(),
                analysis.getTopics(), feedback.getProductLine(), feedback.getRating());
    }

    private String toJson(FeedbackAnalysis analysis) {
        try {
            return objectMapper.writeValueAsString(analysis.getTopics());
        } catch (Exception e) {
            return "[]";
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
