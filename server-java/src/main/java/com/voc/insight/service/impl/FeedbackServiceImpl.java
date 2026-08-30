package com.voc.insight.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voc.insight.ai.AiService;
import com.voc.insight.ai.dto.FeedbackAnalysis;
import com.voc.insight.common.BizException;
import com.voc.insight.common.PageResult;
import com.voc.insight.common.ResultCode;
import com.voc.insight.constant.BusinessDict;
import com.voc.insight.dto.AnalyzeDTO;
import com.voc.insight.dto.FeedbackInput;
import com.voc.insight.dto.FeedbackQueryDTO;
import com.voc.insight.dto.ReviewDTO;
import com.voc.insight.entity.Alert;
import com.voc.insight.entity.Feedback;
import com.voc.insight.mapper.AlertMapper;
import com.voc.insight.mapper.FeedbackMapper;
import com.voc.insight.service.FeedbackProcessService;
import com.voc.insight.service.FeedbackService;
import com.voc.insight.service.FeedbackSourceService;
import com.voc.insight.vo.FeedbackStatsVO;
import com.voc.insight.vo.InsightReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户反馈服务实现。
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {

    private final AiService aiService;
    private final FeedbackProcessService processService;
    private final FeedbackSourceService sourceService;
    private final AlertMapper alertMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ============ 列表查询 ============

    @Override
    public PageResult<Feedback> page(FeedbackQueryDTO query) {
        LambdaQueryWrapper<Feedback> wrapper = buildWrapper(query);

        // urgency 业务顺序（critical 在前）与字典序不同，MyBatis 无法直接表达，
        // 只能全量取出后内存排序。生产环境应加 urgencyOrder 数值字段走数据库排序。
        boolean memorySort = "urgency".equals(query.getSortBy());
        if (memorySort) {
            List<Feedback> all = this.list(wrapper);
            List<Feedback> sorted = sortByUrgency(all);
            int total = sorted.size();
            int page = Math.max(1, query.getPage());
            int limit = Math.max(1, query.getLimit());
            int from = (page - 1) * limit;
            int to = Math.min(from + limit, total);
            List<Feedback> records = from >= total ? List.of() : sorted.subList(from, to);
            return PageResult.of((long) total, (long) page, (long) limit, records);
        }

        applyOrder(wrapper, query.getSortBy(), query.getSortOrder());
        Page<Feedback> p = this.page(new Page<>(query.getPage(), query.getLimit()), wrapper);
        return PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }

    private LambdaQueryWrapper<Feedback> buildWrapper(FeedbackQueryDTO query) {
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(query.getSource()), Feedback::getSource, query.getSource());
        wrapper.eq(StringUtils.hasText(query.getSentiment()), Feedback::getSentiment, query.getSentiment());
        wrapper.eq(StringUtils.hasText(query.getUrgency()), Feedback::getUrgency, query.getUrgency());
        wrapper.eq(StringUtils.hasText(query.getProductLine()), Feedback::getProductLine, query.getProductLine());
        wrapper.eq(StringUtils.hasText(query.getTopicId()), Feedback::getTopicId, query.getTopicId());

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(Feedback::getContent, query.getKeyword())
                    .or()
                    .like(Feedback::getAiSummary, query.getKeyword()));
        }
        if ("true".equals(query.getPendingReview())) {
            wrapper.eq(Feedback::getIsReviewed, false);
        }

        LocalDateTime from = parseTimeRange(query.getTimeRange());
        if (from != null) {
            wrapper.ge(Feedback::getCreatedAt, from);
        }
        return wrapper;
    }

    private LocalDateTime parseTimeRange(String timeRange) {
        if (!StringUtils.hasText(timeRange)) {
            return null;
        }
        return switch (timeRange) {
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "today" -> LocalDateTime.now().toLocalDate().atStartOfDay();
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> null;
        };
    }

    private void applyOrder(LambdaQueryWrapper<Feedback> wrapper, String sortBy, String sortOrder) {
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortBy == null ? "createdAt" : sortBy) {
            case "rating" -> wrapper.orderBy(true, asc, Feedback::getRating);
            case "confidence" -> wrapper.orderBy(true, asc, Feedback::getConfidence);
            case "publishedAt" -> wrapper.orderBy(true, asc, Feedback::getPublishedAt);
            default -> wrapper.orderBy(true, asc, Feedback::getCreatedAt);
        }
    }

    private List<Feedback> sortByUrgency(List<Feedback> list) {
        return list.stream().sorted((a, b) -> {
            int oa = BusinessDict.URGENCY_ORDER.getOrDefault(a.getUrgency(), 4);
            int ob = BusinessDict.URGENCY_ORDER.getOrDefault(b.getUrgency(), 4);
            if (oa != ob) {
                return Integer.compare(oa, ob); // 数值越小越紧急，升序即紧急在前
            }
            // 相同紧急度按创建时间倒序兜底
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        }).toList();
    }

    // ============ 概览统计 ============

    @Override
    public FeedbackStatsVO stats() {
        FeedbackStatsVO vo = new FeedbackStatsVO();
        vo.setTotal(this.count());

        LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
        vo.setToday(this.count(new LambdaQueryWrapper<Feedback>().ge(Feedback::getCreatedAt, today)));
        vo.setNegative(this.count(new LambdaQueryWrapper<Feedback>().eq(Feedback::getSentiment, "negative")));
        vo.setNegativeRatio(vo.getTotal() == 0 ? 0
                : Math.round((double) vo.getNegative() / vo.getTotal() * 1000) / 1000.0);
        vo.setPendingAlert(alertMapper.selectCount(
                new LambdaQueryWrapper<Alert>().eq(Alert::getHandled, false)));
        vo.setPendingReview(this.count(new LambdaQueryWrapper<Feedback>().eq(Feedback::getIsReviewed, false)));
        vo.setAvgRating(avgRating());
        vo.setBySentiment(groupCount("sentiment"));
        vo.setBySource(groupCount("source"));
        vo.setByProduct(groupCount("product_line"));
        return vo;
    }

    private Double avgRating() {
        QueryWrapper<Feedback> qw = new QueryWrapper<>();
        qw.select("AVG(rating) AS avg_rating").isNotNull("rating");
        List<Map<String, Object>> rows = this.baseMapper.selectMaps(qw);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).get("avg_rating") == null) {
            return null;
        }
        return Math.round(((Number) rows.get(0).get("avg_rating")).doubleValue() * 100) / 100.0;
    }

    private Map<String, Long> groupCount(String columnName) {
        QueryWrapper<Feedback> qw = new QueryWrapper<>();
        qw.select(columnName + " AS k", "COUNT(*) AS cnt").groupBy(columnName);
        List<Map<String, Object>> rows = this.baseMapper.selectMaps(qw);
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get("k");
            map.put(k == null ? "unknown" : k.toString(), ((Number) row.get("cnt")).longValue());
        }
        return map;
    }

    // ============ 评分归因报告 ============

    @Override
    public InsightReport insight(String productLine) {
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(productLine)) {
            wrapper.eq(Feedback::getProductLine, productLine);
        }
        List<Feedback> rows = this.list(wrapper.orderByDesc(Feedback::getCreatedAt).last("LIMIT 300"));
        return aiService.generateInsightReport(
                StringUtils.hasText(productLine) ? productLine : "全部产品线", rows);
    }

    // ============ 单条即时分析 ============

    @Override
    public FeedbackAnalysis analyze(AnalyzeDTO dto) {
        FeedbackInput input = new FeedbackInput();
        input.setContent(dto.getContent());
        input.setSource("survey");
        input.setProductLine(dto.getProductLine());
        input.setRating(dto.getRating());
        input.setLanguage(dto.getLanguage());
        return aiService.analyzeFeedback(input, List.of());
    }

    // ============ 导入与演示数据 ============

    @Override
    public int importData(String content, String format) {
        List<FeedbackInput> items = sourceService.parseFeedbackFile(content, format);
        if (items.isEmpty()) {
            throw new BizException(ResultCode.IMPORT_PARSE_FAIL);
        }
        return analyzeAndSave(items);
    }

    @Override
    public int generateDemo(Integer count) {
        int n = Math.min(count == null ? 40 : count, 200);
        List<FeedbackInput> items = sourceService.generateDemoFeedback(n);
        return analyzeAndSave(items);
    }

    /** 批量落库为 raw（is_analyzed=0），由定时任务扫描归因；单条失败不影响整批 */
    private int analyzeAndSave(List<FeedbackInput> items) {
        int created = 0;
        for (FeedbackInput item : items) {
            try {
                if (processService.ingestRaw(item) != null) {
                    created++;
                }
            } catch (Exception e) {
                // 单条失败跳过
            }
        }
        return created;
    }

    // ============ 人工复核 ============

    @Override
    public Feedback review(String id, ReviewDTO dto) {
        Feedback feedback = this.getById(id);
        if (feedback == null) {
            throw new BizException(ResultCode.FEEDBACK_NOT_FOUND);
        }
        try {
            feedback.setHumanLabel(objectMapper.writeValueAsString(dto));
        } catch (Exception e) {
            feedback.setHumanLabel(null);
        }
        if (dto.getSentiment() != null) {
            feedback.setSentiment(dto.getSentiment());
        }
        if (dto.getTopics() != null) {
            try {
                feedback.setTopics(objectMapper.writeValueAsString(dto.getTopics()));
            } catch (Exception ignored) {
            }
        }
        if (dto.getUrgency() != null) {
            feedback.setUrgency(dto.getUrgency());
        }
        feedback.setIsReviewed(true);
        feedback.setConfidence(BigDecimal.ONE); // 人工确认即为终态
        this.updateById(feedback);
        return feedback;
    }
}
