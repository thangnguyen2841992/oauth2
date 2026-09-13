package com.thang.user.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleUserInfo {

    private String sub;

    private String email;

    private Boolean email_verified;

    private String name;

    private String given_name;

    private String family_name;

    private String picture;
}