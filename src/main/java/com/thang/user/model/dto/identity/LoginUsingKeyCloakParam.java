package com.thang.user.model.dto.identity;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class LoginUsingKeyCloakParam {
    String grant_type;

    String client_id;

    String client_secret;

    String username;

    String password;

    String scope;

}
