package com.thang.user.model.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUserResponse {

    private String access_token;
    private String refresh_token;
}
