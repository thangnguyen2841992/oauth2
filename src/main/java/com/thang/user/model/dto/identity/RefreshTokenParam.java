package com.thang.user.model.dto.identity;

import lombok.Data;

@Data
public class RefreshTokenParam {

    private String grant_type = "refresh_token";
    private String client_id;
    private String client_secret;
    private String refresh_token;

}