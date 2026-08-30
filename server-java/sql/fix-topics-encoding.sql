-- 修复：初始化时以 latin1 写入的乱码主题词，删除后以 utf8mb4 重新插入
SET NAMES utf8mb4;

DELETE FROM topic;

INSERT INTO topic (id, text, category, is_active, hit_count, auto_generated, approved)
VALUES
    (UUID(), '理赔时效', '理赔', 1, 0, 0, 1),
    (UUID(), '拒赔争议', '理赔', 1, 0, 0, 1),
    (UUID(), '销售误导', '合规', 1, 0, 0, 1),
    (UUID(), '客服响应', '服务', 1, 0, 0, 1);
