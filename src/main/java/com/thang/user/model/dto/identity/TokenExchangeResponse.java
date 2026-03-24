package com.thang.user.model.dto.identity;

import lombok.*;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TokenExchangeResponse {
    String accessToken;
    String expiresIn;
    String refreshExpiresIn;
    String scope;
    String tokenType;
    String idToken;
}
