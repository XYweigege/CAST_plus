package com.voc.insight.job;

import com.voc.insight.service.InsightService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时归因任务（每日固定批量）。
 * 日间两批（09:00 / 14:00）消化当天新增，夜间一批（23:30）兜底清零未归因，
 * 同时天然承担存量数据回填。频率由 voc.insight.cron / voc.insight.night-cron 控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsightJob {

    private final InsightService insightService;

    /** 日间批量归因：默认 09:00 与 14:00 各一轮 */
    @Scheduled(cron = "${voc.insight.cron}")
    public void run() {
        log.info("========== 开始定时归因（日间批量） ==========");
        try {
            int queued = insightService.runCheck();
            log.info("========== 定时归因完成，投递 {} 个任务 ==========", queued);
        } catch (Exception e) {
            log.error("定时归因失败: {}", e.getMessage(), e);
        }
    }

    /** 夜间兜底批量：默认 23:30，清零当天漏网与存量未归因数据 */
    @Scheduled(cron = "${voc.insight.night-cron}")
    public void runNight() {
        log.info("========== 开始定时归因（夜间兜底） ==========");
        try {
            int queued = insightService.runCheck();
            log.info("========== 夜间兜底归因完成，投递 {} 个任务 ==========", queued);
        } catch (Exception e) {
            log.error("夜间兜底归因失败: {}", e.getMessage(), e);
        }
    }
}
