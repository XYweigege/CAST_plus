-- ============================================================
-- 迁移脚本：新增 sys_user 表（Spring Security 认证）
-- 适用：已初始化的存量库（全新库由 schema.sql 直接建好，无需执行）
-- 幂等：重复执行不报错
-- ============================================================

USE voc_insight;

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
