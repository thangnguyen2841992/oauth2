package com.thang.user.model.dto.identity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class KeycloakSessionResponse {
    private String id;
    private String username;
    private String ipAddress;
    private long start;
    private Long lastAccess;
}
