-- 保险客户声音智能分析系统 - 建库建表
-- MySQL 8 / utf8mb4

-- 客户端连接字符集必须显式指定，否则 docker 初始化时中文种子数据会以 latin1 写入成乱码
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS voc_insight DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE voc_insight;

-- ============================================================
-- 主题词表
-- ============================================================
CREATE TABLE IF NOT EXISTS topic (
    id              VARCHAR(64)  PRIMARY KEY,
    text            VARCHAR(255) NOT NULL COMMENT '主题词',
    category        VARCHAR(100)          COMMENT '归属类别',
    is_active       TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '是否启用',
    hit_count       INT          NOT NULL DEFAULT 0  COMMENT '命中次数，驱动调优闭环',
    auto_generated  TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '是否 AI 生成',
    approved        TINYINT(1)   NOT NULL DEFAULT 1  COMMENT '人工确认状态',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_topic_text (text)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '监控主题词';

-- ============================================================
-- 客户反馈表
-- ============================================================
CREATE TABLE IF NOT EXISTS feedback (
    id              VARCHAR(64)   PRIMARY KEY,
    title           VARCHAR(500)             COMMENT '标题',
    content         TEXT          NOT NULL   COMMENT '反馈正文',
    source          VARCHAR(32)   NOT NULL   COMMENT '渠道编码',
    source_id       VARCHAR(128)             COMMENT '业务系统内唯一 ID，去重依据',
    url             VARCHAR(1000)            COMMENT '原文链接',
    rating          INT                      COMMENT '客户评分 1-5',
    product_line    VARCHAR(32)              COMMENT '产品线编码',
    language        VARCHAR(16)              COMMENT '语言：zh-HK / en / mixed',
    author_name     VARCHAR(100)             COMMENT '客户名（应脱敏）',
    published_at    DATETIME                 COMMENT '反馈发生时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- AI 分析输出
    sentiment       VARCHAR(16)   NOT NULL DEFAULT 'neutral' COMMENT '情感倾向',
    topics          VARCHAR(500)             COMMENT '主题标签 JSON 数组',
    urgency         VARCHAR(16)   NOT NULL DEFAULT 'info'    COMMENT '紧急度',
    urgency_reason  VARCHAR(500)             COMMENT '定级理由',
    ai_summary      VARCHAR(500)             COMMENT '一句话归因',
    confidence      DECIMAL(4, 3)            COMMENT '置信度 0-1',

    -- 人工复核
    human_label     TEXT                     COMMENT '人工修正结果 JSON',
    is_reviewed     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已复核',

    -- 归因状态
    is_analyzed     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已归因（0=待归因，1=已归因）',

    topic_id        VARCHAR(64)              COMMENT '归属主题词 ID',

    UNIQUE KEY uk_source_sid (source, source_id),
    KEY idx_feedback_created_at (created_at),
    KEY idx_feedback_urgency (urgency),
    KEY idx_feedback_sentiment (sentiment),
    KEY idx_feedback_topic_id (topic_id),
    KEY idx_feedback_is_analyzed (is_analyzed)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户反馈';

-- ============================================================
-- 预警表
-- ============================================================
CREATE TABLE IF NOT EXISTS alert (
    id           VARCHAR(64)   PRIMARY KEY,
    type         VARCHAR(32)               COMMENT '预警类型：negative / surge / critical',
    title        VARCHAR(500)  NOT NULL    COMMENT '标题',
    content      TEXT                      COMMENT '内容',
    urgency      VARCHAR(16)   NOT NULL DEFAULT 'info' COMMENT '紧急度',
    is_read      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已读',
    handled      TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '业务是否处置',
    feedback_id  VARCHAR(64)               COMMENT '关联反馈 ID（软关联）',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_alert_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '预警';

-- ============================================================
-- 初始化主题词示例
-- ============================================================
INSERT INTO topic (id, text, category, is_active, hit_count, auto_generated, approved)
VALUES
    (UUID(), '理赔时效', '理赔', 1, 0, 0, 1),
    (UUID(), '拒赔争议', '理赔', 1, 0, 0, 1),
    (UUID(), '销售误导', '合规', 1, 0, 0, 1),
    (UUID(), '客服响应', '服务', 1, 0, 0, 1)
ON DUPLICATE KEY UPDATE id = id;

-- ============================================================
-- 存量数据迁移：已有分析结果的反馈标记为已归因
-- ============================================================
UPDATE feedback SET is_analyzed = 1 WHERE sentiment != 'neutral' OR urgency != 'info';

-- ============================================================
-- 系统用户表（Spring Security：JWT 认证 + 角色授权）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)   NOT NULL COMMENT '用户名',
    password    VARCHAR(100)  NOT NULL COMMENT 'BCrypt 密码哈希',
    role        VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER / SERVICE',
    enabled     TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户';
