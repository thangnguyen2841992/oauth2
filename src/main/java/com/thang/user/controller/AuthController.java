package com.thang.user.controller;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.service.user.IUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/auth")
public class AuthController {

    private final IUserService userService;

    public AuthController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenUserResponse> login(@RequestBody LoginRequest loginRequest) {
        return new ResponseEntity<>(this.userService.login(loginRequest), HttpStatus.OK);
    }

    @GetMapping("/getUUIDClient")
    public ResponseEntity<?> getUUIDClient() {
        return new ResponseEntity<>(userService.getUuidClient(), HttpStatus.OK);
    }

    @GetMapping("/getRoleId/{roleName}")
    public ResponseEntity<?> getRoleId(@PathVariable String roleName) {
        return new ResponseEntity<>(userService.getRoleId(roleName), HttpStatus.OK);
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest user) {
        UserDTO userDTO = userService.createUser(user);
        return new ResponseEntity<>(userDTO, HttpStatus.CREATED);
    }
    @GetMapping("/active")
    public String activeAccount(
            @RequestParam long userId,
            @RequestParam String activeCode,
            Model model
    ) {
        String result = userService.activeUser(userId, activeCode);

        model.addAttribute("message", result);

        if (result.contains("thành công")) {
            return "active-success"; // templates/active-success.html
        } else {
            return "active-failed";  // templates/active-failed.html
        }
    }

}
