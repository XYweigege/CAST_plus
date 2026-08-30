package com.voc.insight.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voc.insight.dto.TopicSaveDTO;
import com.voc.insight.entity.Topic;
import com.voc.insight.vo.TopicExpandVO;

import java.util.List;

/**
 * 主题词服务。
 */
public interface TopicService extends IService<Topic> {

    /** 全部主题词，按命中次数降序 */
    List<Topic> listAll();

    /** 创建主题词（重复时抛业务异常） */
    Topic createTopic(TopicSaveDTO dto);

    /** 更新主题词 */
    Topic updateTopic(String id, TopicSaveDTO dto);

    /** 启停切换 */
    Topic toggle(String id);

    /**
     * AI 扩展主题词为客户口语表达变体。
     * 新生成的变体以 approved=false、isActive=false 落库，需人工确认后才参与监控。
     */
    TopicExpandVO expand(String id);

    /** 人工确认 / 否决 AI 生成的变体 */
    Topic approve(String id, Boolean approved);
}
