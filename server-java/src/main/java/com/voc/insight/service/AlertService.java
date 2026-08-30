package com.voc.insight.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voc.insight.common.PageResult;
import com.voc.insight.entity.Alert;

/**
 * 预警服务。
 */
public interface AlertService extends IService<Alert> {

    /** 分页查询，支持未读 / 未处置过滤 */
    PageResult<Alert> page(Integer page, Integer limit, Boolean unreadOnly, Boolean unhandledOnly);

    /** 未读数 */
    long unreadCount();

    /** 未处置数 */
    long unhandledCount();

    /** 标记已读 */
    Alert markRead(String id);

    /** 全部已读 */
    void markAllRead();

    /** 标记业务已处置 */
    Alert handle(String id);

    /** 清空 */
    void clear();
}
