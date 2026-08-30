package com.voc.insight.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预警。
 * 负面反馈、主题突增等需要业务介入的信号。
 */
@Data
@TableName("alert")
public class Alert implements Serializable {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 预警类型：negative / surge / critical */
    private String type;

    private String title;

    private String content;

    /** 紧急度 */
    private String urgency;

    /** 是否已读（看到了） */
    private Boolean isRead;

    /** 业务是否处置（处理完了） */
    private Boolean handled;

    /** 关联反馈 ID（软关联，突增预警无对应反馈） */
    private String feedbackId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
