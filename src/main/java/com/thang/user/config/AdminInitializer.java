package com.thang.user.config;

import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import com.thang.user.service.user.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.bootstrap.accounts-enabled", havingValue = "true")
public class AdminInitializer {
    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") private String adminPassword;
    @Value("${BOOTSTRAP_STAFF_PASSWORD:}") private String staffPassword;

    @Bean
    CommandLineRunner initAdminAndStaff() {
        return args -> {
            boolean createAdmin = !userRepository.existsByEmail("admin@nihongo-system.local");
            boolean createStaff = !userRepository.existsByEmail("staff@nihongo-system.local");
            if (createAdmin) requirePassword(adminPassword, "BOOTSTRAP_ADMIN_PASSWORD");
            if (createStaff) requirePassword(staffPassword, "BOOTSTRAP_STAFF_PASSWORD");
            if (createAdmin) createUser("admin@nihongo-system.local", "Admin", adminPassword, "ADMIN");
            if (createStaff) createUser("staff@nihongo-system.local", "Staff", staffPassword, "STAFF");
        };
    }
    private void requirePassword(String password, String property) {
        if (!PasswordPolicy.valid(password)) {
            throw new IllegalStateException("Set a valid " + property + " before enabling account bootstrap");
        }
    }
    private void createUser(String email, String lastName, String password, String role) {
        User user = new User();
        user.setUserId(UUID.randomUUID().toString());
        user.setFirstName("System"); user.setLastName(lastName); user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRoleName(role); user.setActive(true);
        LocalDateTime now = LocalDateTime.now();
        user.setDateCreated(now); user.setDateModified(now);
        userRepository.save(user);
        log.info("Created bootstrap {} account; disable account bootstrap after setup", role);
    }
}
