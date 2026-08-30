package com.voc.insight.ai;

import com.voc.insight.constant.BusinessDict;
import com.voc.insight.dto.FeedbackInput;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 构建器。
 * 集中管理所有 AI 提示词，便于迭代与 A/B 对比。
 */
@Component
public class PromptBuilder {

    /**
     * 主题词扩展 Prompt：把书面业务术语翻译为客户口语表达变体。
     */
    public String buildExpandPrompt() {
        return """
                你是保险行业客户体验专家，擅长把书面业务术语翻译成客户的真实口语表达。

                给定一个业务主题词，生成客户在问卷、投诉、社媒中可能使用的各种说法，用于文本匹配。

                规则：
                1. 必须覆盖香港客户的常用表达，包含繁体中文、粤语口语、英文及中英混排
                2. 包含书面说法与缩写
                3. 不要加入与主题无关的泛化词（如"保险""服务"这种任何主题都会出现的词）
                4. 总数控制在 6-15 个

                输出 JSON 数组，只输出 JSON，不要有其他内容。
                示例输入："理赔时效"
                示例输出：["理赔时效","理赔慢","拖咗好耐都未批","幾時先賠到","claim processing delay","slow claim","遲遲未收到賠款","等咗兩個星期"]
                """;
    }

    /**
     * 反馈分析 Prompt：六元组结构化输出。
     *
     * @param item         反馈输入（含产品线、渠道、评分等元信息）
     * @param matchedTerms 预匹配命中的主题词变体，作为给模型的提示
     */
    public String buildAnalysisPrompt(FeedbackInput item, List<String> matchedTerms) {
        String matchHint = (matchedTerms != null && !matchedTerms.isEmpty())
                ? "\n提示：文本预匹配命中了主题词变体：" + String.join("、", matchedTerms)
                : "";

        String metaLine = buildMetaLine(item);

        return """
                你是保险行业客户体验分析专家，负责把客户反馈结构化为可行动的分析结果。

                【可选主题标签】%s
                %s%s

                请分析以下客户反馈并输出：

                1. sentiment：positive / neutral / negative
                2. topics：从【可选主题标签】中选择 1-3 个，**不要自造标签**
                3. urgency：四级定级
                   - critical：涉及拒赔、销售误导、威胁投诉至监管机构（如 IA / 保险投诉局）、扬言退保并公开曝光
                   - action：明确要求跟进、投诉、索赔受阻、多次催促未果
                   - attention：有明确不满但无升级诉求
                   - info：一般咨询、中性描述、或正面反馈
                4. urgencyReason：一句话说明定级依据
                5. aiSummary：一句话点出客户不满或满意的**具体环节**，不要复述原文
                6. confidence：0-1 的置信度。表达模糊、语言混杂、语义不明时给低分

                【语言注意】
                客户可能使用繁体中文、粤语口语或中英混排。以下均为强负面表达：
                "唔賠""搵笨""搞咁耐""拖咗好耐""極不負責任""講一套做一套""no response""waste of time"

                【示例】
                输入：「理賠拖咗兩個星期都未批，打去客服又無人聽，好失望」
                输出：{"sentiment":"negative","topics":["理赔时效","客服响应"],"urgency":"attention","urgencyReason":"索赔审核超两周未出结果且电话渠道无人接听","aiSummary":"索赔审核周期过长且客服电话渠道无人接听","confidence":0.93}

                输入：「Claim was rejected without any clear explanation. Very frustrated and will escalate to the Insurance Authority.」
                输出：{"sentiment":"negative","topics":["拒赔争议","条款清晰度"],"urgency":"critical","urgencyReason":"拒赔未给出清晰解释且明确表示将投诉至监管机构","aiSummary":"拒赔决定缺乏解释，存在监管投诉升级风险","confidence":0.95}

                输入：「客服阿 May 解釋得好清楚，成個流程好順，讚」
                输出：{"sentiment":"positive","topics":["服务态度"],"urgency":"info","urgencyReason":"正面反馈，无风险信号","aiSummary":"客服人员解释清晰，流程体验顺畅","confidence":0.9}

                只输出 JSON，不要有其他内容。
                {"sentiment":"","topics":[],"urgency":"","urgencyReason":"","aiSummary":"","confidence":0}
                """.formatted(
                String.join("、", BusinessDict.TOPIC_TAGS),
                metaLine,
                matchHint
        );
    }

    /**
     * 归因报告 Prompt：基于统计结果与负面摘要归纳。
     */
    public String buildInsightPrompt() {
        return """
                你是保险行业客户体验分析专家。基于一批客户反馈的 AI 摘要，输出评分归因报告。

                要求：
                1. summary：3 句话以内，说明该产品线客户体验的整体状况与主要失分点，要有观点不要罗列
                2. suggestions：3 条具体的改进建议，每条要指向明确的业务环节，可执行，不要空话

                只输出 JSON：{"summary":"...","suggestions":["...","...","..."]}
                """;
    }

    /** 拼接元信息行：产品线、渠道、评分、语言 */
    private String buildMetaLine(FeedbackInput item) {
        StringBuilder sb = new StringBuilder();
        if (item.getProductLine() != null) sb.append("产品线：").append(item.getProductLine()).append("  ");
        if (item.getSource() != null) sb.append("渠道：").append(item.getSource()).append("  ");
        if (item.getRating() != null) sb.append("客户评分：").append(item.getRating()).append("/5  ");
        if (item.getLanguage() != null) sb.append("语言：").append(item.getLanguage());
        return sb.toString().trim();
    }
}
