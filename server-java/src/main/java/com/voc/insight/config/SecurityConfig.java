package com.voc.insight.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voc.insight.common.Result;
import com.voc.insight.common.ResultCode;
import com.voc.insight.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置：JWT 无状态认证 + 角色授权。
 *
 * 权限矩阵：
 * - 公开：/api/auth/login、/api/health、Swagger
 * - /api/csat/**：仅 SERVICE（外部系统服务账号的长期 JWT）
 * - 管理类操作（主题词增删改、导入、生成演示、手动触发分析、删除、签发服务 token）：仅 ADMIN
 * - 其余 /api/**：ADMIN / USER（查看、复核、预警处置）
 *
 * CORS 收编进 Security（MVC 层配置在 Security 启用后对预检请求不生效）。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ---- 公开 ----
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // ---- CSAT 采集：仅服务账号 ----
                        .requestMatchers("/api/csat/**").hasRole("SERVICE")
                        // ---- 管理类操作：仅 ADMIN ----
                        .requestMatchers(HttpMethod.POST, "/api/topics/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/topics/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/topics/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/topics/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/feedbacks/import", "/api/feedbacks/generate-demo").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/feedbacks/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/alerts/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/check-feedbacks").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/auth/service-token").hasRole("ADMIN")
                        // ---- 其余业务接口：ADMIN / USER ----
                        .requestMatchers("/api/**").hasAnyRole("ADMIN", "USER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        // 未认证 / 无权限统一返回项目 Result JSON，而非 Security 默认响应
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, ResultCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, ResultCode.FORBIDDEN))
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** CORS：开发期放开，生产建议收敛到具体域名 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 把 401 / 403 写成统一 Result JSON */
    private void writeError(jakarta.servlet.http.HttpServletResponse response,
                            int status, ResultCode code) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code.getCode(), code.getMessage())));
    }
}
