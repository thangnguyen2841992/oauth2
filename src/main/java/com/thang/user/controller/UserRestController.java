package com.thang.user.controller;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.service.user.IUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin("*")
public class UserRestController {
    private final IUserService userService;

    public UserRestController(IUserService userService) {
        this.userService = userService;
    }

    @PostMapping()
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest user) {
        UserDTO userDTO = userService.createUser(user);
        return new ResponseEntity<>(userDTO, HttpStatus.CREATED);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable String userId) {
        this.userService.deleteUser(userId);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @GetMapping("/getAllUsersKeyCloak")
    public ResponseEntity<?> getAllUsersKeyCloak() {
        return new ResponseEntity<>(userService.getAllUsersKeyCloak(), HttpStatus.OK);
    }


}
