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
 * 系统用户。
 * 角色：ADMIN（管理端全部操作）/ USER（查看与复核）/ SERVICE（外部系统服务账号，仅可调用 /api/csat/**）。
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** BCrypt 密码哈希 */
    private String password;

    /** 角色：ADMIN / USER / SERVICE */
    private String role;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
