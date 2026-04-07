package com.thang.user.model.dto.identity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenExchangeResponse {
    String access_token;
    String refresh_token;
    long expires_in;
    long refresh_expires_in;
    String scope;
    String token_type;
    String id_token;
}
