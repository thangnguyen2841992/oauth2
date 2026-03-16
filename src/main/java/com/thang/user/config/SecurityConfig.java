package com.thang.user.config;

import com.thang.user.JwtAuthConverter;
import com.thang.user.RevokedTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final RevokedTokenService revokedTokenService;
    private final JwtAuthConverter jwtAuthConverter;

    public SecurityConfig(RevokedTokenService revokedTokenService, JwtAuthConverter jwtAuthConverter) {
        this.revokedTokenService = revokedTokenService;
        this.jwtAuthConverter = jwtAuthConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthConverter)
                        )
                );

        // Optional: add a filter to check jti revocation globally (here we rely on JwtAuthConverter to check jti)
        return http.build();
    }
}
