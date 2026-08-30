package com.voc.insight.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voc.insight.common.BizException;
import com.voc.insight.common.PageResult;
import com.voc.insight.common.ResultCode;
import com.voc.insight.entity.Alert;
import com.voc.insight.mapper.AlertMapper;
import com.voc.insight.service.AlertService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {

    @Override
    public PageResult<Alert> page(Integer page, Integer limit, Boolean unreadOnly, Boolean unhandledOnly) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(Alert::getIsRead, false);
        }
        if (Boolean.TRUE.equals(unhandledOnly)) {
            wrapper.eq(Alert::getHandled, false);
        }
        wrapper.orderByDesc(Alert::getCreatedAt);

        Page<Alert> p = this.page(new Page<>(page, limit), wrapper);
        return PageResult.of(p.getTotal(), p.getCurrent(), p.getSize(), p.getRecords());
    }

    @Override
    public long unreadCount() {
        return this.count(new LambdaQueryWrapper<Alert>().eq(Alert::getIsRead, false));
    }

    @Override
    public long unhandledCount() {
        return this.count(new LambdaQueryWrapper<Alert>().eq(Alert::getHandled, false));
    }

    @Override
    public Alert markRead(String id) {
        Alert alert = requireAlert(id);
        alert.setIsRead(true);
        this.updateById(alert);
        return alert;
    }

    @Override
    public void markAllRead() {
        List<Alert> unread = this.list(new LambdaQueryWrapper<Alert>().eq(Alert::getIsRead, false));
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(a -> a.setIsRead(true));
        this.updateBatchById(unread);
    }

    @Override
    public Alert handle(String id) {
        Alert alert = requireAlert(id);
        alert.setHandled(true);
        alert.setIsRead(true);
        this.updateById(alert);
        return alert;
    }

    @Override
    public void clear() {
        this.remove(new LambdaQueryWrapper<>());
    }

    private Alert requireAlert(String id) {
        Alert alert = this.getById(id);
        if (alert == null) {
            throw new BizException(ResultCode.ALERT_NOT_FOUND);
        }
        return alert;
    }
}
