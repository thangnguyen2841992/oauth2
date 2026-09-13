package com.thang.user.config;

import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class AdminInitializer {

    private final IUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner initAdminAndStaff() {
        return args -> {

            // =========================
            // ADMIN
            // =========================
            createUserIfNotExists(
                    "admin@nihongo-system.local",
                    "System",
                    "Admin",
                    "Admin@123",
                    "ADMIN"
            );

            // =========================
            // STAFF
            // =========================
            createUserIfNotExists(
                    "staff@nihongo-system.local",
                    "System",
                    "Staff",
                    "Staff@123",
                    "STAFF"
            );
        };
    }

    private void createUserIfNotExists(
            String email,
            String firstName,
            String lastName,
            String password,
            String role
    ) {

        if (userRepository.existsByEmail(email)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        User user = new User();

        user.setUserId(UUID.randomUUID().toString());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);

        user.setPassword(
                passwordEncoder.encode(password)
        );

        user.setRoleName(role);
        user.setActive(true);

        user.setDateCreated(now);
        user.setDateModified(now);
        user.setLastLogin(null);

        userRepository.save(user);

        System.out.println("======================================");
        System.out.println("Default " + role + " account created");
        System.out.println("Email: " + email);
        System.out.println("Password: " + password);
        System.out.println("Role: " + role);
        System.out.println("======================================");
    }
}