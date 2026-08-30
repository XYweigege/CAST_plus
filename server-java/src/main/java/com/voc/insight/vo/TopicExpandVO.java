package com.voc.insight.vo;

import com.voc.insight.entity.Topic;
import lombok.Data;

import java.util.List;

/** 主题词扩展结果 */
@Data
public class TopicExpandVO {

    /** AI 生成的全部变体 */
    private List<String> variants;

    /** 实际新增入库的变体 */
    private List<Topic> created;
}
