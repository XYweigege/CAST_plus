package com.voc.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voc.insight.common.BizException;
import com.voc.insight.common.ResultCode;
import com.voc.insight.dto.LoginRequest;
import com.voc.insight.dto.LoginResponse;
import com.voc.insight.entity.SysUser;
import com.voc.insight.mapper.SysUserMapper;
import com.voc.insight.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：登录校验与服务账号 token 签发。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 登录：校验用户名密码，签发 JWT。
     * SERVICE 账号不允许交互式登录（其 token 由管理员通过 service-token 接口签发）。
     */
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAIL);
        }
        if ("SERVICE".equals(user.getRole())) {
            throw new BizException(ResultCode.LOGIN_FAIL);
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    /**
     * 为 csat-service 服务账号签发长期 JWT（仅 ADMIN 可调用，权限在 Controller 层控制）。
     */
    public String issueServiceToken() {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "csat-service"));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException("服务账号不存在或已停用");
        }
        return jwtUtil.generateToken(user.getUsername(), user.getRole());
    }
}
