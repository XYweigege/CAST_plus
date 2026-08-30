package com.voc.insight.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 邮件通知服务。
 * 邮件是可选功能：未配置 SMTP 时静默跳过，不影响主流程。
 * 使用 ObjectProvider 注入，避免未配置邮件时启动报错。
 */
@Slf4j
@Service
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final String notifyEmail;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String from,
            @Value("${voc.notify-email:}") String notifyEmail) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.notifyEmail = notifyEmail;
    }

    public boolean isConfigured() {
        return mailSenderProvider.getIfAvailable() != null && StringUtils.hasText(notifyEmail);
    }

    /** 发送客户反馈预警邮件 */
    public void sendAlert(String title, String content, String urgency,
                          List<String> topics, String productLine, Integer rating) {
        if (!isConfigured()) {
            return;
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(StringUtils.hasText(from) ? from : notifyEmail);
            helper.setTo(notifyEmail);
            helper.setSubject("[" + urgencyLabel(urgency) + "] 客户反馈预警：" + (topics.isEmpty() ? "需跟进" : topics.get(0)));
            helper.setText(buildHtml(title, content, urgency, topics, productLine, rating), true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("发送预警邮件失败: {}", e.getMessage());
        }
    }

    private String urgencyLabel(String urgency) {
        return switch (urgency) {
            case "critical" -> "紧急";
            case "action" -> "需处理";
            case "attention" -> "需关注";
            default -> "一般";
        };
    }

    private String buildHtml(String title, String content, String urgency,
                             List<String> topics, String productLine, Integer rating) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family:-apple-system,sans-serif;max-width:600px;margin:0 auto;padding:20px;">
                  <div style="background:#1e3a8a;color:#fff;padding:16px 20px;border-radius:8px 8px 0 0;">
                    <h3 style="margin:0;">客户反馈预警</h3>
                    <p style="margin:6px 0 0;font-size:12px;opacity:.85;">保险客户声音智能分析系统</p>
                  </div>
                  <div style="background:#f8f9fa;padding:20px;border-radius:0 0 8px 8px;">
                    <p><strong style="color:#dc2626;">[%s]</strong> %s</p>
                    <div style="background:#fff;border-left:4px solid #1e3a8a;padding:12px 16px;margin:14px 0;">%s</div>
                    <p style="color:#666;font-size:13px;">
                      主题标签：%s<br/>
                      产品线：%s<br/>
                      客户评分：%s<br/>
                      触发时间：%s
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(
                urgencyLabel(urgency), title, content,
                topics.isEmpty() ? "—" : String.join("、", topics),
                productLine == null ? "—" : productLine,
                rating == null ? "—" : rating + " / 5",
                time
        );
    }
}
