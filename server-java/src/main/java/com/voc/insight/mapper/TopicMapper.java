package com.voc.insight.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voc.insight.entity.Topic;

/**
 * Topic Mapper。
 * 继承 BaseMapper 即拥有 CRUD；复杂查询用 QueryWrapper 在 Service 层组装。
 */
public interface TopicMapper extends BaseMapper<Topic> {
}
