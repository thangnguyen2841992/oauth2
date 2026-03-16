package com.example.jwtjwks.security;

import com.thang.user.RevokedTokenService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtAuthConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private final RevokedTokenService revokedTokenService;

    // These are defensive; JwtDecoder already validates issuer/audience when configured.
    private static final String EXPECTED_ISS = "https://auth.example.com/";
    private static final String EXPECTED_AUD = "api://default";

    public JwtAuthConverter(RevokedTokenService revokedTokenService) {
        this.revokedTokenService = revokedTokenService;
    }

    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        // Defensive checks (optional)
        if (jwt.getIssuer() == null || !EXPECTED_ISS.equals(jwt.getIssuer().toString())) {
            throw oauth2Error("invalid_token", "Invalid issuer");
        }

        if (jwt.getAudience() == null || !jwt.getAudience().contains(EXPECTED_AUD)) {
            throw oauth2Error("invalid_token", "Invalid audience");
        }

        String jti = jwt.getId();
        if (jti != null && revokedTokenService.isRevoked(jti)) {
            throw oauth2Error("invalid_token", "Token revoked");
        }

        Collection<GrantedAuthority> authorities = extractAuthoritiesFromClaims(jwt);
        String principalName = (jwt.getSubject() != null) ? jwt.getSubject() : jwt.getClaimAsString("sub");
        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }

    private Collection<GrantedAuthority> extractAuthoritiesFromClaims(Jwt jwt) {
        Set<String> authStrings = new HashSet<>();

        Object scope = jwt.getClaim("scope");
        if (scope instanceof String) {
            String[] scopes = ((String) scope).split(" ");
            for (String s : scopes) {
                if (!s.isBlank()) authStrings.add("SCOPE_" + s);
            }
        }

        Object roles = jwt.getClaim("roles");
        if (roles instanceof Collection) {
            ((Collection<?>) roles).forEach(r -> {
                String role = String.valueOf(r).trim();
                if (!role.isEmpty()) authStrings.add("ROLE_" + role);
            });
        } else if (roles instanceof String) {
            String[] parts = ((String) roles).split(",");
            for (String p : parts) {
                if (!p.isBlank()) authStrings.add("ROLE_" + p.trim());
            }
        }

        return authStrings.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }

    private OAuth2AuthenticationException oauth2Error(String errorCode, String description) {
        OAuth2Error err = new OAuth2Error(errorCode, description, null);
        return new OAuth2AuthenticationException(err);
    }
}