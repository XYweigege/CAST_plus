package com.voc.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.voc.insight.ai.AiService;
import com.voc.insight.ai.dto.FeedbackAnalysis;
import com.voc.insight.constant.BusinessDict;
import com.voc.insight.dto.FeedbackInput;
import com.voc.insight.entity.Alert;
import com.voc.insight.entity.Feedback;
import com.voc.insight.entity.Topic;
import com.voc.insight.mapper.AlertMapper;
import com.voc.insight.mapper.FeedbackMapper;
import com.voc.insight.mapper.TopicMapper;
import com.voc.insight.mq.AnalyzeTaskMessage;
import com.voc.insight.mq.AnalyzeTaskProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 洞察分析核心流程（DB 驱动归因）。
 *
 * 流程：CSAT/导入/演示数据先落库（is_analyzed=0）
 * → 定时任务扫描待归因反馈，投递归因任务到 MQ（预热主题词扩展缓存）
 * → 消费者读库 → 预匹配 → AI 归因（一条反馈一次 LLM 调用，遍历全部激活主题词）
 * → UPDATE 写回同一行（is_analyzed=1）→ 推送/预警 → 突增检测。
 *
 * 生产接入点：外部产品调用 CsatController 的 ingest 接口落库即可。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    /** 突增预警冷却 key 前缀，TTL 即静默期 */
    private static final String SURGE_COOLDOWN_KEY = "voc:surge:cooldown:";

    private final TopicMapper topicMapper;
    private final FeedbackMapper feedbackMapper;
    private final AlertMapper alertMapper;
    private final AiService aiService;
    private final FeedbackProcessService processService;
    private final NotifyService notifyService;
    private final AnalyzeTaskProducer analyzeTaskProducer;
    private final StringRedisTemplate redisTemplate;

    /** 单轮扫描投递的待归因反馈上限 */
    @Value("${voc.insight.batch-size:200}")
    private int batchSize;

    @Value("${voc.insight.surge-threshold:5}")
    private int surgeThreshold;

    @Value("${voc.insight.surge-cooldown-hours:12}")
    private int surgeCooldownHours;

    /**
     * 执行一轮归因调度：扫描待归因反馈 + 投递 MQ，返回投递的归因任务数。
     * 归因结果由消费者异步写回数据库。
     */
    public int runCheck() {
        // 预热全部激活主题词的扩展缓存（Redis），消费者直接命中，避免并发重复调 AI
        List<Topic> topics = topicMapper.selectList(
                new LambdaQueryWrapper<Topic>().eq(Topic::getIsActive, true));
        if (topics.isEmpty()) {
            log.info("无激活主题词，跳过本轮归因");
            return 0;
        }
        for (Topic topic : topics) {
            try {
                aiService.expandTopic(topic.getText());
            } catch (Exception e) {
                log.warn("主题 [{}] 扩展预热失败: {}", topic.getText(), e.getMessage());
            }
        }

        // 扫描待归因反馈（is_analyzed=0），限量投递
        List<Feedback> pending = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getIsAnalyzed, false)
                        .orderByAsc(Feedback::getCreatedAt)
                        .last("LIMIT " + batchSize));
        if (pending.isEmpty()) {
            log.info("无待归因反馈，跳过本轮");
            detectSurge();
            return 0;
        }

        int queued = 0;
        for (Feedback feedback : pending) {
            try {
                analyzeTaskProducer.send(new AnalyzeTaskMessage(feedback.getId()));
                queued++;
            } catch (Exception e) {
                log.error("归因任务投递失败 (feedbackId={}): {}", feedback.getId(), e.getMessage());
            }
        }

        // 突增检测（读已落库数据，留在生产者侧同步执行）
        detectSurge();

        log.info("本轮归因调度完成，投递 {} 个任务", queued);
        return queued;
    }

    /**
     * 消费一个归因任务：读库 → 预匹配全部激活主题词 → AI 归因 → 写回同一行。
     * 一条反馈只调用一次 LLM（归属判断在归因结果基础上做），由 AnalyzeTaskConsumer 调用。
     */
    public void processAnalyzeTask(String feedbackId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            return; // 反馈已删除，丢弃任务
        }
        if (Boolean.TRUE.equals(feedback.getIsAnalyzed())) {
            return; // 已归因（重复投递或人工复核过），幂等跳过
        }

        // 遍历激活主题词做预匹配，选出命中词最多的主题词作为归属
        List<Topic> topics = topicMapper.selectList(
                new LambdaQueryWrapper<Topic>().eq(Topic::getIsActive, true));
        String bestTopicId = null;
        List<String> bestMatched = new ArrayList<>();
        for (Topic topic : topics) {
            List<String> expanded = aiService.expandTopic(topic.getText());
            List<String> matched = aiService.preMatch(feedback.getContent(), expanded);
            if (matched.size() > bestMatched.size()) {
                bestMatched = matched;
                bestTopicId = topic.getId();
            }
        }

        // AI 归因（六元组），携带预匹配命中词辅助判断
        FeedbackInput input = toInput(feedback);
        FeedbackAnalysis analysis = aiService.analyzeFeedback(input, bestMatched);

        // 归属判断：预匹配命中（字面）或 AI 判定主题与主题词相关（语义）
        String topicId = resolveTopicId(bestTopicId, bestMatched, analysis, topics);

        // 写回同一行并标记已归因
        feedback.setSentiment(analysis.getSentiment());
        feedback.setTopics(processService.toTopicsJson(analysis));
        feedback.setUrgency(analysis.getUrgency());
        feedback.setUrgencyReason(analysis.getUrgencyReason());
        feedback.setAiSummary(analysis.getAiSummary());
        feedback.setConfidence(java.math.BigDecimal.valueOf(analysis.getConfidence()));
        // 低置信度判定不写入终态，等待人工复核
        feedback.setIsReviewed(analysis.getConfidence() >= BusinessDict.CONFIDENCE_THRESHOLD);
        feedback.setTopicId(topicId);
        feedback.setIsAnalyzed(true);
        feedbackMapper.updateById(feedback);

        // 主题命中计数（驱动关键词调优闭环）
        if (topicId != null) {
            topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                    .eq(Topic::getId, topicId)
                    .setSql("hit_count = hit_count + 1"));
        }

        // 归因完成后推送 + 按紧急度预警
        processService.afterAnalysis(feedback, analysis);
    }

    /** 归属判断：预匹配命中优先；否则看 AI 主题标签是否与某主题词相关 */
    private String resolveTopicId(String bestTopicId, List<String> bestMatched,
                                  FeedbackAnalysis analysis, List<Topic> topics) {
        if (!bestMatched.isEmpty()) {
            return bestTopicId;
        }
        for (Topic topic : topics) {
            boolean related = analysis.getTopics().stream().anyMatch(t ->
                    t.equals(topic.getText()) || topic.getText().contains(t) || t.contains(topic.getText()));
            if (related) {
                return topic.getId();
            }
        }
        return null;
    }

    /** 实体 → 分析输入对象 */
    private FeedbackInput toInput(Feedback feedback) {
        FeedbackInput input = new FeedbackInput();
        input.setContent(feedback.getContent());
        input.setTitle(feedback.getTitle());
        input.setSource(feedback.getSource());
        input.setSourceId(feedback.getSourceId());
        input.setUrl(feedback.getUrl());
        input.setRating(feedback.getRating());
        input.setProductLine(feedback.getProductLine());
        input.setLanguage(feedback.getLanguage());
        input.setAuthorName(feedback.getAuthorName());
        input.setPublishedAt(feedback.getPublishedAt());
        return input;
    }

    /**
     * 突增检测：某主题 24 小时内负面反馈超过阈值，往往是系统性问题的前兆。
     * 单条反馈看不出来，需要聚合后判断。带静默期避免重复告警。
     */
    private void detectSurge() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Feedback> negatives = feedbackMapper.selectList(
                new LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getSentiment, "negative")
                        .ge(Feedback::getCreatedAt, since)
                        .select(Feedback::getTopics));

        Map<String, Integer> counter = new HashMap<>();
        for (Feedback row : negatives) {
            for (String t : aiService.parseTopicsJson(row.getTopics())) {
                counter.merge(t, 1, Integer::sum);
            }
        }

        LocalDateTime cooldownFrom = LocalDateTime.now().minusHours(surgeCooldownHours);
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            String topicText = entry.getKey();
            int count = entry.getValue();
            if (count < surgeThreshold || !BusinessDict.TOPIC_TAGS.contains(topicText)) {
                continue;
            }

            // 静默期：Redis 冷却 key 快速判断（TTL 即静默期），DB 查询兜底
            if (inSurgeCooldown(topicText, cooldownFrom)) {
                continue;
            }

            Alert alert = new Alert();
            alert.setType("surge");
            alert.setTitle("主题突增：" + topicText);
            alert.setContent(String.format(
                    "过去 24 小时内「%s」相关负面反馈达 %d 条，超出常规水平，建议排查是否为系统性问题。",
                    topicText, count));
            alert.setUrgency("action");
            alertMapper.insert(alert);
            markSurgeCooldown(topicText);

            notifyService.push("alert", Map.of(
                    "id", alert.getId(),
                    "title", alert.getTitle(),
                    "content", alert.getContent(),
                    "urgency", alert.getUrgency()
            ));
            log.info("主题突增预警：{}（{} 条/24h）", topicText, count);
        }
    }

    /** 冷却判断：优先 Redis；Redis 不可用时回退原 DB 查询 */
    private boolean inSurgeCooldown(String topicText, LocalDateTime cooldownFrom) {
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(SURGE_COOLDOWN_KEY + topicText))) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Redis 冷却判断不可用，回退数据库查询: {}", e.getMessage());
        }
        Long recent = alertMapper.selectCount(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getType, "surge")
                .like(Alert::getTitle, topicText)
                .ge(Alert::getCreatedAt, cooldownFrom));
        return recent > 0;
    }

    /** 告警落库后写入冷却 key，TTL 到期自动解除静默 */
    private void markSurgeCooldown(String topicText) {
        try {
            redisTemplate.opsForValue().set(
                    SURGE_COOLDOWN_KEY + topicText, "1", Duration.ofHours(surgeCooldownHours));
        } catch (Exception e) {
            log.warn("写入突增冷却失败: {}", e.getMessage());
        }
    }
}
