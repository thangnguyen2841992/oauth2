package com.thang.user.service.user;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.model.dto.identity.UserKeyCloakResponse;

import java.util.List;
import java.util.Map;

public interface IUserService {
    void createUser(CreateUserRequest dto);
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Long id);
    UserDTO updateUser(Long id, UserDTO dto);
    void deleteUser(String userId);

    TokenUserResponse login(LoginRequest loginRequest);

    List<UserKeyCloakResponse> getAllUsersKeyCloak();

    Map<String, String> getUuidClient();
    void getRoleId(String roleName);
    String activeUser(long userId, String activeCode);
    String resendActiveCode(long userId);
    String extractUsername(String token);
}
