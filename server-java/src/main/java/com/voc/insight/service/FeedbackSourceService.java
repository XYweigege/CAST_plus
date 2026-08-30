package com.voc.insight.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voc.insight.dto.FeedbackInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 反馈数据源服务。
 * 真实场景中反馈来自问卷、索赔、客服等系统的接口或文件导出。
 * 本服务内置一套贴近香港保险客户表达习惯的语料，
 * 用于生成演示数据与 AI 标注效果评测集；同时提供 CSV / JSON 文件导入解析。
 *
 * 生产接入点：定时任务中的 fetchNewFeedback() 只需替换为调用业务系统接口。
 */
@Slf4j
@Service
public class FeedbackSourceService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    // ============ 语料库 ============
    // 每条自带人工标注（topic / sentiment），可直接作为评测集。

    private record CorpusEntry(String text, String topic, String sentiment, String productLine) {
    }

    private static final List<CorpusEntry> CORPUS = List.of(
            // 理赔时效
            new CorpusEntry("理賠拖咗三個星期都未批，打電話又無人聽，好失望", "理赔时效", "negative", "medical"),
            new CorpusEntry("Claim submitted 3 weeks ago, still no update. Very poor service.", "理赔时效", "negative", "travel"),
            new CorpusEntry("理賠好快，三日就收到錢，效率一流", "理赔时效", "positive", "travel"),
            new CorpusEntry("Claim was settled within 3 days. Excellent service.", "理赔时效", "positive", "travel"),
            // 赔付金额争议
            new CorpusEntry("賠得咁少，醫療費兩萬只賠三千，根本唔合理", "赔付金额争议", "negative", "medical"),
            new CorpusEntry("Settlement amount is way lower than expected and the deduction was never explained.", "赔付金额争议", "negative", "medical"),
            // 拒赔争议
            new CorpusEntry("明明買咗全保，最後話唔賠，搵笨！", "拒赔争议", "negative", "travel"),
            new CorpusEntry("Claim rejected without any clear explanation. I will escalate this to the Insurance Authority.", "拒赔争议", "negative", "medical"),
            new CorpusEntry("拒賠理由牽強，我準備去保險投訴局投訴", "拒赔争议", "negative", "accident"),
            // 核保与投保
            new CorpusEntry("投保時話乜都保，核保時問長問短，最後仲要加價", "核保与投保", "negative", "medical"),
            new CorpusEntry("Underwriting took forever and they kept asking for more documents.", "核保与投保", "negative", "medical"),
            new CorpusEntry("投保流程好順暢，幾分鐘就搞掂", "核保与投保", "positive", "travel"),
            // 客服响应
            new CorpusEntry("打咗五次客服都無人接，等到火滾", "客服响应", "negative", "motor"),
            new CorpusEntry("Customer service never replies to my emails.", "客服响应", "negative", "travel"),
            new CorpusEntry("客服跟進到位，主動打電話 update 進度，值得一讚", "客服响应", "positive", "medical"),
            // 服务态度
            new CorpusEntry("客服阿 May 解釋得好清楚，好有耐性，讚", "服务态度", "positive", "travel"),
            new CorpusEntry("The agent was very patient and explained everything clearly.", "服务态度", "positive", "medical"),
            new CorpusEntry("職員態度冷淡，問多兩句就唔耐煩", "服务态度", "negative", "motor"),
            // 条款清晰度
            new CorpusEntry("條款寫到好含糊，完全睇唔明咩情況先賠", "条款清晰度", "negative", "travel"),
            new CorpusEntry("Terms and conditions are too vague. No one can understand what is actually covered.", "条款清晰度", "negative", "pet"),
            // 销售误导
            new CorpusEntry("當初 agent 話全保，原來一堆除外責任，講一套做一套", "销售误导", "negative", "medical"),
            new CorpusEntry("The agent promised full coverage but there are many exclusions. This is misleading.", "销售误导", "negative", "travel"),
            new CorpusEntry("sales 話呢份係儲蓄計劃，原來係保險，感覺被誤導，要求退保", "销售误导", "negative", "accident"),
            // 续保与退保
            new CorpusEntry("續保保費加咗三成，事前完全無通知", "续保与退保", "negative", "motor"),
            new CorpusEntry("Premium increased 30% at renewal without any prior notice.", "续保与退保", "negative", "medical"),
            new CorpusEntry("請問續保手續如何辦理？", "续保与退保", "neutral", "motor"),
            // APP 与网站体验
            new CorpusEntry("個 app 好難用，upload document 次次 fail", "APP与网站体验", "negative", "travel"),
            new CorpusEntry("The app keeps crashing when I upload documents. Very frustrating.", "APP与网站体验", "negative", "medical"),
            new CorpusEntry("新版 app 界面清晰，索償進度一目了然", "APP与网站体验", "positive", "travel"),
            // 价格与性价比
            new CorpusEntry("保費年年加，保障年年減，性價比低", "价格与性价比", "negative", "medical"),
            new CorpusEntry("Price is reasonable for the coverage provided, quite satisfied.", "价格与性价比", "positive", "travel"),
            // 理赔资料繁琐
            new CorpusEntry("索償要交十幾份文件，缺一份又打回頭，搞咁耐", "理赔资料繁琐", "negative", "medical"),
            new CorpusEntry("Too many documents required for such a simple claim.", "理赔资料繁琐", "negative", "travel"),
            new CorpusEntry("May I know what documents are needed for a travel insurance claim?", "理赔资料繁琐", "neutral", "travel"),
            // 中性 / 正面补充
            new CorpusEntry("保單已收到，謝謝", "核保与投保", "neutral", "travel"),
            new CorpusEntry("整體滿意，會推薦俾朋友", "服务态度", "positive", "pet")
    );

    private static final List<String> SOURCE_POOL = List.of("survey", "claim", "service", "social", "appstore", "email");
    private static final List<String> PRODUCT_POOL = List.of("travel", "medical", "accident", "home", "motor", "pet");
    private static final List<String> AUTHOR_POOL = List.of("陳小姐", "李先生", "Wong Tai Man", "張太", "K. Chan", "匿名客戶", "Ho Siu Ming", "劉先生");

    // ============ 演示数据生成 ============

    /**
     * 生成演示用反馈数据。
     * 语料自带人工标注，可直接作为 AI 标注效果评测集。
     */
    public List<FeedbackInput> generateDemoFeedback(int count) {
        List<FeedbackInput> items = new ArrayList<>();
        long ts = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            CorpusEntry entry = CORPUS.get(random.nextInt(CORPUS.size()));
            String source = SOURCE_POOL.get(random.nextInt(SOURCE_POOL.size()));
            // 80% 概率沿用语料的产品线，20% 随机打散，更接近真实分布
            String productLine = (random.nextDouble() < 0.8 && entry.productLine() != null)
                    ? entry.productLine()
                    : PRODUCT_POOL.get(random.nextInt(PRODUCT_POOL.size()));

            FeedbackInput input = new FeedbackInput();
            input.setTitle(entry.text().length() > 24 ? entry.text().substring(0, 24) + "…" : entry.text());
            input.setContent(entry.text());
            input.setSource(source);
            input.setSourceId("demo-" + ts + "-" + i);
            input.setRating(ratingBySentiment(entry.sentiment()));
            input.setProductLine(productLine);
            input.setLanguage(detectLanguage(entry.text()));
            if (random.nextDouble() < 0.7) {
                input.setAuthorName(AUTHOR_POOL.get(random.nextInt(AUTHOR_POOL.size())));
            }
            input.setPublishedAt(LocalDateTime.now().minusDays(random.nextInt(30)));
            items.add(input);
        }
        return items;
    }

    // ============ 文件导入解析 ============

    public List<FeedbackInput> parseFeedbackFile(String raw, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return parseCsv(raw);
        }
        return parseJson(raw);
    }

    private List<FeedbackInput> parseJson(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode list = root.isArray() ? root : root.path("data");
            List<FeedbackInput> result = new ArrayList<>();
            if (!list.isArray()) {
                return result;
            }
            int i = 0;
            for (JsonNode node : list) {
                String content = node.path("content").asText(node.path("text").asText(""));
                if (content.isEmpty()) {
                    continue;
                }
                FeedbackInput input = new FeedbackInput();
                input.setContent(content);
                input.setTitle(node.path("title").asText(null));
                input.setSource(node.path("source").asText("survey"));
                input.setSourceId(node.path("sourceId").asText("import-" + System.currentTimeMillis() + "-" + i));
                input.setRating(node.hasNonNull("rating") ? node.get("rating").asInt() : null);
                input.setProductLine(node.path("productLine").asText(null));
                input.setLanguage(node.path("language").asText(detectLanguage(content)));
                input.setAuthorName(node.path("authorName").asText(null));
                result.add(input);
                i++;
            }
            return result;
        } catch (Exception e) {
            log.error("JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<FeedbackInput> parseCsv(String raw) {
        List<FeedbackInput> result = new ArrayList<>();
        String[] lines = raw.split("\\r?\\n");
        if (lines.length < 2) {
            return result;
        }
        String[] headers = lines[0].split(",");
        int contentIdx = indexOf(headers, "content") >= 0 ? indexOf(headers, "content") : indexOf(headers, "text");
        if (contentIdx < 0) {
            return result;
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",");
            String content = cols.length > contentIdx ? cols[contentIdx].trim() : "";
            if (content.isEmpty()) {
                continue;
            }
            FeedbackInput input = new FeedbackInput();
            input.setContent(content);
            input.setTitle(pick(headers, cols, "title"));
            input.setSource(pick(headers, cols, "source") != null ? pick(headers, cols, "source") : "survey");
            input.setSourceId(pick(headers, cols, "sourceId") != null
                    ? pick(headers, cols, "sourceId") : "import-" + System.currentTimeMillis() + "-" + i);
            String rating = pick(headers, cols, "rating");
            input.setRating(rating != null ? Integer.parseInt(rating) : null);
            input.setProductLine(pick(headers, cols, "productLine"));
            input.setLanguage(pick(headers, cols, "language") != null
                    ? pick(headers, cols, "language") : detectLanguage(content));
            input.setAuthorName(pick(headers, cols, "authorName"));
            result.add(input);
        }
        return result;
    }

    private String pick(String[] headers, String[] cols, String name) {
        int idx = indexOf(headers, name);
        return (idx >= 0 && idx < cols.length && !cols[idx].trim().isEmpty()) ? cols[idx].trim() : null;
    }

    private int indexOf(String[] arr, String name) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].trim().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    // ============ 辅助 ============

    /** 简易语言识别 */
    private String detectLanguage(String text) {
        boolean hasCJK = text.matches(".*[\\u4e00-\\u9fff].*");
        boolean hasLatin = text.matches(".*[a-zA-Z]{3,}.*");
        if (hasCJK && hasLatin) {
            return "mixed";
        }
        return hasCJK ? "zh-HK" : "en";
    }

    /** 按情感生成合理评分：负面 1-2，中性 3，正面 4-5，保证数据一致性 */
    private Integer ratingBySentiment(String sentiment) {
        return switch (sentiment) {
            case "negative" -> 1 + random.nextInt(2);
            case "positive" -> 4 + random.nextInt(2);
            default -> 3;
        };
    }
}
