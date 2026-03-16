package com.thang.user.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtDecoderConfig {

    // Set these to your authorization server values
    private static final String ISSUER = "https://auth.example.com/";
    private static final String AUDIENCE = "api://default";

    @Bean
    public JwtDecoder jwtDecoder() {
        // Build decoder from OIDC issuer (will fetch JWKS)
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(ISSUER);

        // Combine default issuer validator and custom audience validator
        OAuth2TokenValidator<Jwt> defaultWithIssuer = JwtValidators.createDefaultWithIssuer(ISSUER);
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(AUDIENCE);
        OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(defaultWithIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(combined);
        return jwtDecoder;
    }
}
