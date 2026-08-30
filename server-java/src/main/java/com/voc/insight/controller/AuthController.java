package com.voc.insight.controller;

import com.voc.insight.common.Result;
import com.voc.insight.dto.LoginRequest;
import com.voc.insight.dto.LoginResponse;
import com.voc.insight.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：登录、当前用户信息、服务账号 token 签发。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录，返回 JWT 与角色 */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 当前登录用户信息（从 SecurityContext 读取，无需查库） */
    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<Map<String, String>> me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .orElse("");
        return Result.success(Map.of(
                "username", authentication.getName(),
                "role", role
        ));
    }

    /** 为 csat-service 服务账号签发长期 JWT（仅 ADMIN，权限由 SecurityConfig 控制） */
    @Operation(summary = "签发 CSAT 服务账号长期 token（仅管理员）")
    @PostMapping("/service-token")
    public Result<Map<String, String>> serviceToken() {
        return Result.success(Map.of("token", authService.issueServiceToken()));
    }
}
