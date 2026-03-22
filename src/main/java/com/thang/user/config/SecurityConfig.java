package com.thang.user.config;

import com.thang.user.model.Role;
import com.thang.user.model.User;
import com.thang.user.service.role.IRoleService;
import com.thang.user.service.user.IUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    private final IRoleService roleService;
    private final IUserService userService;
    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public SecurityConfig(IRoleService roleService, IUserService userService) {
        this.roleService = roleService;
        this.userService = userService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login**", "/css/**", "/js/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/oauth2/authorization/keycloak") // optional custom login endpoint
                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(this.oidcUserService()) // for OIDC
                        )
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                );

        return http.build();
    }

    // OidcUserService to retain default behaviour but allow us to map roles/claims
    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        // You can map extra attributes here if needed
        return new OidcUserService();
    }

    // Success handler: provision user into DB on successful login
    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        return new AuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                                org.springframework.security.core.@NonNull Authentication authentication)
                    throws IOException, ServletException {

                OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

                // extract useful attributes
                assert oauthUser != null;
                String username = Optional.ofNullable((String) oauthUser.getAttribute("preferred_username"))
                        .orElse((String) oauthUser.getAttribute("preferred_username"));
                String email = Optional.ofNullable((String) oauthUser.getAttribute("email")).orElse(username);
                String sub = Optional.ofNullable((String) oauthUser.getAttribute("sub")).orElse(username);

                // extract roles from claim "realm_access" -> roles (Keycloak default)
                Set<String> roleNames = extractRolesFromAttributes(oauthUser.getAttributes());

                // provision user
                provisionUser(username, email, roleNames);

                // redirect to default page
                response.sendRedirect("/");
            }
        };
    }

    private Set<String> extractRolesFromAttributes(Map<String, Object> attributes) {
        Object realmAccess = attributes.get("realm_access");
        if (realmAccess instanceof Map) {
            Object roles = ((Map<?, ?>) realmAccess).get("roles");
            if (roles instanceof Collection) {
                return ((Collection<?>) roles).stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet());
            }
        }
        // fallback: possibly resource_access.<client>.roles
        Object resourceAccess = attributes.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            for (Object val : resourceMap.values()) {
                if (val instanceof Map) {
                    Object roles = ((Map<?,?>) val).get("roles");
                    if (roles instanceof Collection) {
                        return ((Collection<?>) roles).stream()
                                .map(Object::toString)
                                .collect(Collectors.toSet());
                    }
                }
            }
        }
        return Collections.emptySet();
    }

    // provision user into DB (simplified)
    private void provisionUser(String username, String email, Set<String> roleNames) {
        List<Role> roles = this.roleService.getAllRoles();
        if (roles.isEmpty()) {
            Role roleAdmin = new Role();
            roleAdmin.setRoleName("ROLE_ADMIN");
            this.roleService.addNewRole(roleAdmin);
            Role roleUser = new Role();
            roleUser.setRoleName("ROLE_USER");
            this.roleService.addNewRole(roleUser);
        }
        List<User> users = this.userService.getAllUsers();
        if (users.isEmpty()) {
            User user = new User();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("thuThuy@1"));
            user.setEmail("admin@nihongo.com");
            user.setPhoneNumber("0394910427");
            user.setAddress("Ha Noi");
            user.setPlainTextDEK("111111111111111111111");
            user.setDateOfBirth(new Date());
            user.setDateCreated(new Date());
            user.setDateModified(new Date());
            Role role = this.roleService.findRoleByRoleName("ROLE_ADMIN");
            Set<Role> roleSet = new HashSet<>();
            roleSet.add(role);
            user.setRoles(roleSet);
            this.userService.addUser(user);
        }
    }


}
