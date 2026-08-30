package com.voc.insight.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发 / 解析 / 校验。
 * 普通用户 token 有效期由 voc.security.jwt-expiration-hours 控制；
 * 服务账号（SERVICE 角色）token 有效期由 voc.security.service-token-expiration-days 控制（默认 365 天）。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationHours;
    private final long serviceTokenDays;

    public JwtUtil(@Value("${voc.security.jwt-secret}") String secret,
                   @Value("${voc.security.jwt-expiration-hours:8}") long expirationHours,
                   @Value("${voc.security.service-token-expiration-days:365}") long serviceTokenDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationHours = expirationHours;
        this.serviceTokenDays = serviceTokenDays;
    }

    /** 签发 token：subject 为用户名，claim role 为角色 */
    public String generateToken(String username, String role) {
        boolean service = "SERVICE".equals(role);
        long ttlMillis = service
                ? serviceTokenDays * 24 * 60 * 60 * 1000
                : expirationHours * 60 * 60 * 1000;
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，失败（签名错误 / 过期）返回 null。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
