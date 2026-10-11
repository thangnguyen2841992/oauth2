package com.thang.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtCookieFilter jwtCookieFilter;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(
            JwtCookieFilter jwtCookieFilter,
            JwtAuthenticationConverter jwtAuthenticationConverter) {

        this.jwtCookieFilter = jwtCookieFilter;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, com.nihongo.security.BrowserOriginPolicy origins, com.nihongo.security.InternalCredentials credentials,
            com.thang.user.service.user.SessionService sessions,org.springframework.core.env.Environment env) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/active-user/**").permitAll()
                        .requestMatchers("/images/**").permitAll()

                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")

                        .requestMatchers("/api/users/**")
                        .hasAnyRole("ADMIN", "STAFF", "USER")

                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        http.addFilterBefore(
                jwtCookieFilter,
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class
        );
        http.addFilterBefore(new com.nihongo.security.BrowserOriginServletFilter(origins,credentials,env.getProperty("security.origin-check.enabled",Boolean.class,true)),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        http.addFilterAfter(new UserSessionValidationFilter(sessions,env.getProperty("security.session-validation.enabled",Boolean.class,true)),org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}
