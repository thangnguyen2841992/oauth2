package com.thang.user.model.dto.identity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserKeyCloakResponse {
    String sub;

    String email_verified;

    String preferred_username;
}
