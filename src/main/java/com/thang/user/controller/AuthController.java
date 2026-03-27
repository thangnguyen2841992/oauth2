package com.thang.user.controller;

import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.identity.TokenExchangeResponse;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.service.user.IUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/users/auth")
public class AuthController {

    private final IUserService userService;

    public AuthController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenUserResponse> login(@RequestBody LoginRequest loginRequest) {
        return new ResponseEntity<>(this.userService.login(loginRequest), HttpStatus.OK);
    }

}
