package com.voc.insight.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应：token + 用户身份。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT，前端放入 Authorization: Bearer 头 */
    private String token;

    /** 用户名 */
    private String username;

    /** 角色：ADMIN / USER / SERVICE */
    private String role;
}
