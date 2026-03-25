package com.thang.user.controller;

import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.identity.TokenExchangeResponse;
import com.thang.user.service.user.IUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final IUserService userService;

    public AuthController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenExchangeResponse> login(@RequestBody LoginRequest loginRequest) {
        return new ResponseEntity<>(this.userService.login(loginRequest), HttpStatus.OK);
    }

}
