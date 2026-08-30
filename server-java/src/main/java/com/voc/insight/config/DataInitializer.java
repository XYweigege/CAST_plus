package com.voc.insight.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voc.insight.entity.SysUser;
import com.voc.insight.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 种子账号初始化：首次启动时创建 admin / viewer / csat-service。
 * 已存在则跳过，不覆盖已修改的密码。
 * 上线后请立即修改默认密码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${voc.security.seed-admin-password:admin123}")
    private String adminPassword;

    @Value("${voc.security.seed-viewer-password:viewer123}")
    private String viewerPassword;

    @Value("${voc.security.seed-service-password:csat-service-2026}")
    private String servicePassword;

    @Override
    public void run(String... args) {
        seedUser("admin", adminPassword, "ADMIN");
        seedUser("viewer", viewerPassword, "USER");
        seedUser("csat-service", servicePassword, "SERVICE");
    }

    /** 不存在才创建 */
    private void seedUser(String username, String rawPassword, String role) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        sysUserMapper.insert(user);
        log.info("已创建种子账号: {} ({})", username, role);
    }
}
