package com.thang.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Value("${api.client-id}")
    private String clientId;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        Object resourceAccessObj = jwt.getClaim("resource_access");

        if (!(resourceAccessObj instanceof Map<?, ?> resourceAccess)) {
            return List.of();
        }

        Object clientObj = resourceAccess.get(clientId);

        if (!(clientObj instanceof Map<?, ?> client)) {
            return List.of();
        }

        Object rolesObj = client.get("roles");

        if (!(rolesObj instanceof List<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}