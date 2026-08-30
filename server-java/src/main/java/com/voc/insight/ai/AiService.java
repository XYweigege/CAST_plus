package com.voc.insight.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voc.insight.ai.dto.FeedbackAnalysis;
import com.voc.insight.constant.BusinessDict;
import com.voc.insight.dto.FeedbackInput;
import com.voc.insight.entity.Feedback;
import com.voc.insight.vo.InsightReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 分析服务。
 * 封装：主题词扩展、反馈结构化标注、评分归因报告、规则兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final AiClient aiClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 主题词扩展结果缓存，同一主题词不重复调用 AI */
    private final Map<String, List<String>> expansionCache = new ConcurrentHashMap<>();

    // ========== 主题词扩展 ==========

    /**
     * 把书面业务术语扩展为客户口语表达变体。
     * 解决"业务人员维护书面语、客户说的是口语"导致的匹配召回率低的问题。
     */
    public List<String> expandTopic(String topic) {
        List<String> cached = expansionCache.get(topic);
        if (cached != null) {
            return cached;
        }

        List<String> coreTerms = extractCoreTerms(topic);

        if (!aiClient.isConfigured()) {
            List<String> result = merge(topic, coreTerms, List.of());
            expansionCache.put(topic, result);
            return result;
        }

        try {
            String content = aiClient.chat(promptBuilder.buildExpandPrompt(), topic, 0.2, 400);
            List<String> parsed = parseStringArray(content);
            List<String> result = merge(topic, coreTerms, parsed);
            expansionCache.put(topic, result);
            log.info("主题词扩展 [{}] -> {} 个变体", topic, result.size());
            return result;
        } catch (Exception e) {
            log.error("主题词扩展失败: {}", e.getMessage());
            List<String> fallback = merge(topic, coreTerms, List.of());
            expansionCache.put(topic, fallback);
            return fallback;
        }
    }

    private List<String> merge(String topic, List<String> coreTerms, List<String> aiVariants) {
        Set<String> set = new LinkedHashSet<>();
        set.add(topic);
        set.addAll(coreTerms);
        aiVariants.stream().map(String::trim).filter(s -> !s.isEmpty()).forEach(set::add);
        return new ArrayList<>(set);
    }

    /** 从主题词中提取核心词（纯文本方式，不依赖 AI） */
    private List<String> extractCoreTerms(String topic) {
        String[] parts = topic.split("[\\s\\-_/\\\\·、，,]+");
        List<String> terms = new ArrayList<>();
        if (parts.length > 1) {
            for (String p : parts) {
                if (p.length() >= 2) terms.add(p);
            }
            for (int i = 0; i < parts.length - 1; i++) {
                terms.add(parts[i] + parts[i + 1]);
            }
        }
        return terms.stream()
                .filter(t -> !t.equalsIgnoreCase(topic))
                .distinct()
                .collect(Collectors.toList());
    }

    // ========== 预匹配 ==========

    /** 检查文本命中了哪些扩展词（不区分大小写），返回命中词列表 */
    public List<String> preMatch(String text, List<String> expanded) {
        String lower = text.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (String kw : expanded) {
            if (lower.contains(kw.toLowerCase())) {
                matched.add(kw);
            }
        }
        return matched;
    }

    // ========== 反馈标注 ==========

    /**
     * AI 结构化标注（六元组）。
     * AI 不可用时走规则兜底，保证流程不中断。
     */
    public FeedbackAnalysis analyzeFeedback(FeedbackInput item, List<String> matchedTerms) {
        if (!aiClient.isConfigured()) {
            return fallbackAnalysis(item);
        }

        try {
            String prompt = promptBuilder.buildAnalysisPrompt(item, matchedTerms);
            String content = aiClient.chat(prompt, truncate(item.getContent(), 2000), 0.1, 500);
            return parseAnalysis(content);
        } catch (Exception e) {
            log.error("AI 分析失败: {}", e.getMessage());
            return fallbackAnalysis(item);
        }
    }

    /** 解析 AI 输出：JSON 提取 + 枚举校验 + 置信度钳制（三重防线） */
    private FeedbackAnalysis parseAnalysis(String content) {
        try {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalStateException("AI 响应未包含 JSON");
            }
            JsonNode root = objectMapper.readTree(content.substring(start, end + 1));

            FeedbackAnalysis analysis = new FeedbackAnalysis();

            String sentiment = root.path("sentiment").asText("neutral");
            analysis.setSentiment(BusinessDict.SENTIMENTS.contains(sentiment) ? sentiment : "neutral");

            // 主题标签白名单过滤：模型可能自造标签，直接丢弃
            List<String> topics = new ArrayList<>();
            JsonNode topicsNode = root.path("topics");
            if (topicsNode.isArray()) {
                for (JsonNode t : topicsNode) {
                    String tag = t.asText();
                    if (BusinessDict.TOPIC_TAGS.contains(tag)) {
                        topics.add(tag);
                    }
                }
            }
            analysis.setTopics(topics);

            String urgency = root.path("urgency").asText("info");
            analysis.setUrgency(BusinessDict.URGENCIES.contains(urgency) ? urgency : "info");

            analysis.setUrgencyReason(truncate(root.path("urgencyReason").asText(""), 200));
            analysis.setAiSummary(truncate(root.path("aiSummary").asText(""), 200));

            // 置信度钳制在 0-1，防止模型输出异常值污染数据
            double confidence = root.path("confidence").asDouble(0.5);
            analysis.setConfidence(Math.max(0, Math.min(1, confidence)));

            return analysis;
        } catch (Exception e) {
            throw new RuntimeException("解析 AI 输出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 规则兜底：只做负面识别，不做正面识别。
     * 保守策略——宁可把正面判成中性（无害），也不要把负面判成中性（漏掉风险）。
     * confidence 固定 0.3，会自动进入待复核队列。
     */
    private FeedbackAnalysis fallbackAnalysis(FeedbackInput item) {
        String text = item.getContent() == null ? "" : item.getContent().toLowerCase();
        String[] negativeHints = {"唔賠", "搵笨", "失望", "投訴", "拖", "慢", "差",
                "reject", "delay", "slow", "bad", "worst"};

        boolean isNegative = false;
        for (String hint : negativeHints) {
            if (text.contains(hint)) {
                isNegative = true;
                break;
            }
        }
        if (item.getRating() != null && item.getRating() <= 2) {
            isNegative = true;
        }

        FeedbackAnalysis analysis = new FeedbackAnalysis();
        analysis.setSentiment(isNegative ? "negative" : "neutral");
        analysis.setTopics(new ArrayList<>());
        analysis.setUrgency(isNegative ? "attention" : "info");
        analysis.setUrgencyReason("未配置 AI 服务，使用规则兜底判定");
        analysis.setAiSummary(truncate(item.getContent(), 60));
        analysis.setConfidence(0.3);
        return analysis;
    }

    // ========== 评分归因报告 ==========

    /**
     * 统计部分由代码计算（可复现、可对账），
     * 归纳与建议部分交给 LLM（语义任务）。
     * 刻意不让模型做计数任务——那是它不擅长且容易编造的地方。
     */
    public InsightReport generateInsightReport(String productLine, List<Feedback> rows) {
        InsightReport report = new InsightReport();
        report.setProductLine(productLine);
        report.setTotalFeedback(rows.size());

        // 平均评分
        List<Feedback> rated = rows.stream().filter(r -> r.getRating() != null).toList();
        report.setAvgRating(rated.isEmpty() ? null
                : Math.round(rated.stream().mapToInt(Feedback::getRating).average().orElse(0) * 100) / 100.0);

        // 负面占比
        long negCount = rows.stream().filter(r -> "negative".equals(r.getSentiment())).count();
        report.setNegativeRatio(rows.isEmpty() ? 0 : Math.round((double) negCount / rows.size() * 1000) / 1000.0);

        // 主题分布
        Map<String, int[]> topicMap = new HashMap<>(); // topic -> [count, negCount]
        for (Feedback r : rows) {
            for (String t : parseTopicsJson(r.getTopics())) {
                int[] arr = topicMap.computeIfAbsent(t, k -> new int[2]);
                arr[0]++;
                if ("negative".equals(r.getSentiment())) {
                    arr[1]++;
                }
            }
        }
        List<InsightReport.TopicStat> topTopics = topicMap.entrySet().stream()
                .map(e -> {
                    InsightReport.TopicStat stat = new InsightReport.TopicStat();
                    stat.setTopic(e.getKey());
                    stat.setCount(e.getValue()[0]);
                    stat.setNegativeCount(e.getValue()[1]);
                    return stat;
                })
                .sorted((a, b) -> b.getCount() - a.getCount())
                .collect(Collectors.toList());
        report.setTopTopics(topTopics);

        String summary = String.format("共 %d 条反馈，负面占比 %.1f%%。",
                rows.size(), report.getNegativeRatio() * 100);
        List<String> suggestions = new ArrayList<>();

        if (!aiClient.isConfigured() || rows.isEmpty()) {
            if (!topTopics.isEmpty()) {
                summary += "主要集中于：" + topTopics.stream().limit(3)
                        .map(InsightReport.TopicStat::getTopic)
                        .collect(Collectors.joining("、")) + "。";
            }
            report.setSummary(summary);
            report.setSuggestions(suggestions);
            return report;
        }

        // 负面摘要样本：只取前 30 条，控制 token 成本
        String sampleSummaries = rows.stream()
                .filter(r -> "negative".equals(r.getSentiment()))
                .limit(30)
                .map(r -> r.getAiSummary() != null ? r.getAiSummary() : truncate(r.getContent(), 80))
                .collect(Collectors.joining("\n"));

        try {
            String topicDist = topTopics.stream().limit(8)
                    .map(t -> t.getTopic() + "(" + t.getCount() + ")")
                    .collect(Collectors.joining("、"));
            String userContent = """
                    产品线：%s
                    反馈总数：%d，负面占比：%.1f%%
                    平均评分：%s
                    主题分布：%s

                    负面反馈摘要样本：
                    %s
                    """.formatted(
                    productLine, rows.size(), report.getNegativeRatio() * 100,
                    report.getAvgRating() == null ? "N/A" : report.getAvgRating(),
                    topicDist, sampleSummaries);

            String content = aiClient.chat(promptBuilder.buildInsightPrompt(), userContent, 0.3, 600);
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                JsonNode root = objectMapper.readTree(content.substring(start, end + 1));
                summary = root.path("summary").asText(summary);
                JsonNode sugNode = root.path("suggestions");
                if (sugNode.isArray()) {
                    for (JsonNode s : sugNode) {
                        suggestions.add(s.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.error("归因报告生成失败: {}", e.getMessage());
        }

        report.setSummary(summary);
        report.setSuggestions(suggestions.stream().limit(5).collect(Collectors.toList()));
        return report;
    }

    /** 解析 topics JSON 数组字符串 */
    public List<String> parseTopicsJson(String topicsJson) {
        if (topicsJson == null || topicsJson.isEmpty()) {
            return List.of();
        }
        try {
            List<String> result = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(topicsJson)) {
                result.add(node.asText());
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseStringArray(String content) {
        try {
            int start = content.indexOf('[');
            int end = content.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(content.substring(start, end + 1))) {
                result.add(node.asText());
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
