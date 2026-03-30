package com.thang.user.model.dto.identity;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetUuidClientResponse {
    String id;
    String clientId;
    String name;
    String description;
    String rootUrl;
    String adminUrl;
    String baseUrl;
    String surrogateAuthRequired;
    String enabled;
    String alwaysDisplayInConsole;
    String clientAuthenticatorType;
    String secret;

}
