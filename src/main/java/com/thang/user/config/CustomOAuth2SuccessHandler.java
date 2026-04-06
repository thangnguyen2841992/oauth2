package com.thang.user.config;

import com.thang.user.service.user.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private final IUserService userService;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User user = (OAuth2User) authentication.getPrincipal();

        assert user != null;
        String email = user.getAttribute("email");
        String name = user.getAttribute("name");

        // 🔥 LẤY KEYCLOAK USER ID
        String keycloakUserId = user.getAttribute("sub");

        String lastName = "";

        this.userService.createUserFromGoogle(email, name, lastName, keycloakUserId);


        response.sendRedirect("http://localhost:8082/api/auth/home");
    }
}
