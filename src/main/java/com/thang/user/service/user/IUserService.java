package com.thang.user.service.user;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.model.dto.identity.UserKeyCloakResponse;
import com.thang.user.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface IUserService {
    User createUser(CreateUserRequest dto);

    List<UserDTO> getAllUsers();

    UserDTO getUserById(Long id);

    UserDTO updateUser(Long id, UserDTO dto);

    void deleteUser(String userId);

    TokenUserResponse login(LoginRequest loginRequest);

    List<UserKeyCloakResponse> getAllUsersKeyCloak();

    Map<String, String> getUuidClient();

    String activeUser(long userId, String activeCode);

    String resendActiveCode(long userId);

    String extractUsername(String token);

    void createUserFromGoogle(String email,
                              String firstName,
                              String lastName,
                              String keycloakUserId);


    TokenUserResponse exchangeCodeToToken(String code);
    TokenUserResponse handleOAuth2Login(String code);
}
