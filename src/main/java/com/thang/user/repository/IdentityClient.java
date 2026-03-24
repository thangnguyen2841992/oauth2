package com.thang.user.repository;

import com.thang.user.model.dto.identity.TokenExchangeParam;
import com.thang.user.model.dto.identity.TokenExchangeResponse;
import com.thang.user.model.dto.identity.UserCreationParam;
import feign.Body;
import feign.QueryMap;
import jakarta.annotation.PostConstruct;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-client", url = "${spring.idp.url}")
public interface IdentityClient {

    @PostMapping(value = "/realms/${client.idp.realm}/protocol/openid-connect/token",
    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    TokenExchangeResponse exchangeClientToken(@QueryMap() TokenExchangeParam param);

    @PostMapping(value = "/admin/realms/${client.idp.realm}/users",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<?> createNewUser(@RequestBody() UserCreationParam body, @RequestHeader("authorization") String token);




}
