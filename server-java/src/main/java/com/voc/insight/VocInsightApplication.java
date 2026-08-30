package com.voc.insight;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 保险客户声音智能分析系统 - 启动类
 *
 * @EnableScheduling  开启定时任务（insightJob）
 * @MapperScan        扫描 MyBatis-Plus Mapper 接口，避免逐个加 @Mapper
 */
@EnableScheduling
@MapperScan("com.voc.insight.mapper")
@SpringBootApplication
public class VocInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(VocInsightApplication.class, args);
        System.out.println("""
                
                ================================================
                📊 保险客户声音智能分析系统（Java 版）启动成功
                🌐 API:        http://localhost:3001/api
                📖 Swagger UI: http://localhost:3001/swagger-ui.html
                ================================================
                """);
    }
}
