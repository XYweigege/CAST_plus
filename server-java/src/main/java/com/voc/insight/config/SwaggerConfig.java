package com.voc.insight.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI 文档配置。
 * 访问 http://localhost:3001/swagger-ui.html 查看。
 * 右上角可按业务模块切换分组，便于前端按需查阅。
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI vocOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("保险客户声音智能分析系统 API")
                        .description("""
                                VoC Insight 后端接口文档（Java / Spring Boot / MySQL）。

                                通用约定：
                                - 所有接口（除 SSE）返回统一结构 `Result{code, message, data, timestamp}`，`code=0` 表示成功；
                                - 参数校验失败返回 `code=400`，`message` 为具体字段错误，多个错误以「；」分隔；
                                - 业务错误码从 1000 开始（如 1002 主题词不存在、1003 反馈不存在）。
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("VoC Insight"))
                        .license(new License().name("Internal Use Only")))
                .servers(List.of(
                        new Server().url("http://localhost:3001").description("本地开发环境")
                ));
    }

    /** 客户反馈：列表、统计、分析、导入、复核 */
    @Bean
    public GroupedOpenApi feedbackApi() {
        return GroupedOpenApi.builder()
                .group("1-客户反馈")
                .pathsToMatch("/api/feedbacks/**")
                .build();
    }

    /** 主题词：监控主题的维护与 AI 扩展 */
    @Bean
    public GroupedOpenApi topicApi() {
        return GroupedOpenApi.builder()
                .group("2-主题词管理")
                .pathsToMatch("/api/topics/**")
                .build();
    }

    /** 预警中心：预警列表与处置 */
    @Bean
    public GroupedOpenApi alertApi() {
        return GroupedOpenApi.builder()
                .group("3-预警中心")
                .pathsToMatch("/api/alerts/**")
                .build();
    }

    /** 系统：健康检查、SSE 推送、手动触发分析 */
    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("4-系统与实时通知")
                .pathsToMatch("/api/notify/**", "/api/health", "/api/check-feedbacks")
                .build();
    }
}
