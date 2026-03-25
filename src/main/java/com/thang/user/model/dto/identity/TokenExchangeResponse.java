package com.thang.user.model.dto.identity;

import lombok.*;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
//@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TokenExchangeResponse {
    String access_token;
    String refresh_token;
    long expires_in;
    long refresh_expires_in;
    String scope;
    String token_type;
    String id_token;
}
