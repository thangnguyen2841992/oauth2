package com.thang.user.service.user;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.TokenExchangeResponse;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.model.entity.User;

import java.util.List;

public interface IUserService {
    UserDTO createUser(CreateUserRequest dto);
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Long id);
    UserDTO updateUser(Long id, UserDTO dto);
    void deleteUser(String userId);

    TokenUserResponse login(LoginRequest loginRequest);
}
