package com.thang.user.model.dto;

import lombok.Data;

@Data
public class GoogleSetupPasswordRequest {

    private String setupToken;

    private String password;

    private String confirmPassword;
}