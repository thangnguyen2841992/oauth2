package com.thang.user.service.user;

import com.thang.user.model.dto.GoogleTokenResponse;
import com.thang.user.model.dto.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    @Value("${google.client-id}")
    private String clientId;

    @Value("${google.client-secret}")
    private String clientSecret;

    @Value("${google.redirect-uri}")
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();


    // =========================================================
    // CODE -> GOOGLE ACCESS TOKEN -> GOOGLE USER
    // =========================================================

    public GoogleUserInfo getGoogleUser(String code) {

        if (code == null || code.isBlank()) {
            throw new RuntimeException("Google code không hợp lệ");
        }

        // =====================================================
        // 1. CODE -> ACCESS TOKEN
        // =====================================================

        String tokenUrl =
                "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_FORM_URLENCODED
        );

        MultiValueMap<String, String> body =
                new LinkedMultiValueMap<>();

        body.add("code", code);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<GoogleTokenResponse> tokenResponse =
                restTemplate.exchange(
                        tokenUrl,
                        HttpMethod.POST,
                        request,
                        GoogleTokenResponse.class
                );

        GoogleTokenResponse googleToken =
                tokenResponse.getBody();

        if (googleToken == null ||
                googleToken.getAccess_token() == null ||
                googleToken.getAccess_token().isBlank()) {

            throw new RuntimeException(
                    "Không lấy được Google access token"
            );
        }

        // =====================================================
        // 2. ACCESS TOKEN -> USER INFO
        // =====================================================

        String userInfoUrl =
                "https://www.googleapis.com/oauth2/v3/userinfo";

        HttpHeaders userHeaders =
                new HttpHeaders();

        userHeaders.setBearerAuth(
                googleToken.getAccess_token()
        );

        HttpEntity<Void> userRequest =
                new HttpEntity<>(userHeaders);

        ResponseEntity<GoogleUserInfo> userResponse =
                restTemplate.exchange(
                        userInfoUrl,
                        HttpMethod.GET,
                        userRequest,
                        GoogleUserInfo.class
                );

        GoogleUserInfo googleUser =
                userResponse.getBody();

        if (googleUser == null ||
                googleUser.getEmail() == null ||
                googleUser.getEmail().isBlank()) {

            throw new RuntimeException(
                    "Không lấy được email Google"
            );
        }

        // =====================================================
        // 3. PHẢI VERIFY EMAIL
        // =====================================================

        if (!Boolean.TRUE.equals(
                googleUser.getEmail_verified()
        )) {

            throw new RuntimeException(
                    "Google email chưa được xác thực"
            );
        }

        return googleUser;
    }
}