package com.openevent.identity.bootstrap;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.openevent.identity.auth.dto.ValidPasswordValidator;
import com.openevent.identity.user.RoleName;
import com.openevent.identity.user.RoleRepository;
import com.openevent.identity.user.User;
import com.openevent.identity.user.UserRepository;

import lombok.RequiredArgsConstructor;

/** Tạo tài khoản admin đầu tiên từ cấu hình. Không đụng tới tài khoản nếu email đã tồn tại. */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapProperties properties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.adminEmail()) || !StringUtils.hasText(properties.adminPassword())) {
            log.info("Chưa cấu hình tài khoản admin đầu tiên, bỏ qua");
            return;
        }
        if (!ValidPasswordValidator.isValid(properties.adminPassword())) {
            throw new IllegalArgumentException("openevent.bootstrap.admin-password phải dài 8 ký tự đến 72 byte");
        }
        String email = properties.adminEmail().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) {
            return;
        }
        User admin = User.local(email, passwordEncoder.encode(properties.adminPassword()), "Administrator");
        admin.addRole(roles.findByName(RoleName.USER).orElseThrow());
        admin.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        users.save(admin);
        log.info("Đã tạo tài khoản admin đầu tiên: {}", email);
    }
}
