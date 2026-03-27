package com.thang.user.model.dto.identity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserKeyCloakResponse {
    String id;

    String username;

    String emailVerified;
    String createdTimestamp;
    String enabled;
    String totp;
//    String disableableCredentialTypes;
    String notBefore;

    Access access;
}
