package com.thang.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginResponse {

    private String status;

    private String setupToken;

    private String email;

    private String firstName;

    private String lastName;

    private TokenUserResponse token;
}