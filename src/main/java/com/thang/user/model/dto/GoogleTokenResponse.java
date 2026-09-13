package com.thang.user.model.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleTokenResponse {

    private String access_token;

    private String token_type;

    private Integer expires_in;

    private String refresh_token;

    private String scope;

    private String id_token;
}