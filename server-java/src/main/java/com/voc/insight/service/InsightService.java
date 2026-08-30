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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 洞察分析核心流程。
 *
 * 流程：拉取激活主题词 → 采集反馈 → 逐主题词（扩展 → 预匹配 → AI 分析 → 归属判断
 * → 保存 → 命中计数 → 推送/预警）→ 突增检测。
 *
 * 生产接入点：fetchNewFeedback() 只需替换为业务系统接口 / 文件导出轮询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightService {

    private final TopicMapper topicMapper;
    private final FeedbackMapper feedbackMapper;
    private final AlertMapper alertMapper;
    private final AiService aiService;
    private final FeedbackSourceService sourceService;
    private final FeedbackProcessService processService;
    private final NotifyService notifyService;

    @Value("${voc.insight.batch-size:12}")
    private int batchSize;

    @Value("${voc.insight.surge-threshold:5}")
    private int surgeThreshold;

    @Value("${voc.insight.surge-cooldown-hours:12}")
    private int surgeCooldownHours;

    private int batchSeq = 0;

    /**
     * 执行一轮分析，返回新增反馈数。
     */
    public int runCheck() {
        List<Topic> topics = topicMapper.selectList(
                new LambdaQueryWrapper<Topic>().eq(Topic::getIsActive, true));
        if (topics.isEmpty()) {
            log.info("无激活主题词，跳过本轮分析");
            return 0;
        }

        List<FeedbackInput> items = fetchNewFeedback();
        log.info("采集到 {} 条新增反馈，覆盖 {} 个主题词", items.size(), topics.size());

        int newCount = 0;
        for (Topic topic : topics) {
            try {
                // 1) 主题词扩展：把书面业务术语翻译成客户口语表达
                List<String> expanded = aiService.expandTopic(topic.getText());

                for (FeedbackInput item : items) {
                    // 2) 预匹配：未命中扩展词的反馈可能无关，但仍送 AI 兜底语义判断
                    List<String> matched = aiService.preMatch(item.getContent(), expanded);

                    // 3) AI 结构化分析
                    FeedbackAnalysis analysis = aiService.analyzeFeedback(item, matched);

                    // 4) 归属判断：命中扩展词（字面）或 AI 判定的主题与该主题词相关（语义）
                    boolean topicMatched = !matched.isEmpty() || isTopicRelated(analysis, topic.getText());
                    if (!topicMatched) {
                        continue;
                    }

                    // 5) 保存 + 推送 + 预警（去重在内部处理）
                    Feedback saved = processService.saveFeedback(item, analysis, topic.getId());
                    if (saved != null) {
                        newCount++;
                        // 6) 主题命中计数（驱动关键词调优闭环）
                        topicMapper.update(null, new LambdaUpdateWrapper<Topic>()
                                .eq(Topic::getId, topic.getId())
                                .setSql("hit_count = hit_count + 1"));
                    }
                }
            } catch (Exception e) {
                // 单个主题词失败不影响其他
                log.error("主题 [{}] 分析失败: {}", topic.getText(), e.getMessage());
            }
        }

        // 7) 突增检测
        detectSurge();

        log.info("本轮分析完成，新增 {} 条反馈", newCount);
        return newCount;
    }

    /**
     * 采集一批新增反馈。
     * 生产环境替换为：survey → 问卷系统 API；claim → 索赔系统；service → 客服工单系统。
     */
    private List<FeedbackInput> fetchNewFeedback() {
        batchSeq++;
        int seq = batchSeq;
        List<FeedbackInput> items = sourceService.generateDemoFeedback(batchSize);
        // sourceId 必须全局唯一，否则会被去重逻辑丢弃
        items.forEach(item -> item.setSourceId("b" + seq + "-" + item.getSourceId()));
        return items;
    }

    /** 判断 AI 判定的主题与当前主题词是否相关（语义归属兜底） */
    private boolean isTopicRelated(FeedbackAnalysis analysis, String topicText) {
        return analysis.getTopics().stream().anyMatch(t ->
                t.equals(topicText) || topicText.contains(t) || t.contains(topicText));
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

            // 静默期：一段时间内已告警过的同主题不再重复告警
            Long recent = alertMapper.selectCount(new LambdaQueryWrapper<Alert>()
                    .eq(Alert::getType, "surge")
                    .like(Alert::getTitle, topicText)
                    .ge(Alert::getCreatedAt, cooldownFrom));
            if (recent > 0) {
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

            notifyService.push("alert", Map.of(
                    "id", alert.getId(),
                    "title", alert.getTitle(),
                    "content", alert.getContent(),
                    "urgency", alert.getUrgency()
            ));
            log.info("主题突增预警：{}（{} 条/24h）", topicText, count);
        }
    }
}
