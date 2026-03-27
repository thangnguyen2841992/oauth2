package com.thang.user.model.dto.identity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUserResponse {
    String access_token;
    String refresh_token;
    String expires_in;
    String refresh_expires_in;
    String scope;
    String token_type;
    String id_token;
    String session_state;
}
