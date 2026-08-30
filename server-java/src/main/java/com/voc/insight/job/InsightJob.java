package com.voc.insight.job;

import com.voc.insight.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时分析任务。
 * 频率由配置 voc.insight.cron 控制，默认每 30 分钟一轮。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightJob {

    private final InsightService insightService;

    @Scheduled(cron = "${voc.insight.cron}")
    public void run() {
        log.info("========== 开始定时分析 ==========");
        try {
            int created = insightService.runCheck();
            log.info("========== 定时分析完成，新增 {} 条 ==========", created);
        } catch (Exception e) {
            log.error("定时分析失败: {}", e.getMessage(), e);
        }
    }
}
