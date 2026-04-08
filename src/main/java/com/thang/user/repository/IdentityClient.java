package com.thang.user.repository;

import com.thang.user.model.dto.identity.*;
import feign.QueryMap;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "identity-client", url = "${spring.idp.url}")
public interface IdentityClient {

    @PostMapping(value = "/realms/${spring.idp.realm}/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    TokenExchangeResponse exchangeClientToken(@QueryMap() TokenExchangeParam param);

    @PostMapping(value = "/admin/realms/${spring.idp.realm}/users",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<?> createNewUser(@RequestBody() UserCreationParam body, @RequestHeader("authorization") String token);

    @DeleteMapping(value = "/admin/realms/${spring.idp.realm}/users/{userId}")
    ResponseEntity<?> deleteUser(@PathVariable String userId, @RequestHeader("authorization") String token);


    @PostMapping(value = "/realms/${spring.idp.realm}/protocol/openid-connect/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    TokenUserResponse login(@QueryMap() LoginUsingKeyCloakParam param);


    @GetMapping(value = "/admin/realms/${spring.idp.realm}/users?exact=false")
    List<UserKeyCloakResponse> getAllUsersKeyCloak(@RequestHeader("authorization") String token);


    @GetMapping(value = "/admin/realms/${spring.idp.realm}/clients?clientId=${spring.idp.client-id}")
    List<GetUuidClientResponse> getUuidClient(@RequestHeader("authorization") String token);


    @GetMapping(value = "/admin/realms/${spring.idp.realm}/clients/{clientUUID}/roles/{roleName}")
    GetRoleIdResponse getRoleId(@RequestHeader("authorization") String token, @PathVariable String clientUUID, @PathVariable String roleName);

    @PostMapping(value = "/admin/realms/${spring.idp.realm}/users/{userId}/role-mappings/clients/{clientUUID}")
    GetRoleIdResponse mappingRoleToUser(@RequestHeader("authorization") String token, @PathVariable String userId, @PathVariable String clientUUID, @RequestBody List<GetRoleIdResponse> roles);

    @PutMapping("/admin/realms/${spring.idp.realm}/users/{userId}/execute-actions-email")
    void executeActionsEmail(
            @RequestHeader("Authorization") String token,
            @PathVariable String userId,
            @RequestBody List<String> actions,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("lifespan") Integer lifespan
    );
    @GetMapping("/admin/realms/${spring.idp.realm}/roles/{roleName}")
    GetRoleIdResponse getRealmRole(
            @RequestHeader("authorization") String token,
            @PathVariable String roleName
    );
    @PostMapping("/admin/realms/${spring.idp.realm}/users/{userId}/role-mappings/realm")
    void mappingRealmRoleToUser(
            @RequestHeader("authorization") String token,
            @PathVariable String userId,
            @RequestBody List<GetRoleIdResponse> roles
    );
}
