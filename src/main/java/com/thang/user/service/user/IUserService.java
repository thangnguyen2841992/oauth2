package com.thang.user.service.user;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.TokenUserResponse;
import com.thang.user.model.dto.identity.UserKeyCloakResponse;
import com.thang.user.model.entity.User;

import java.util.List;
import java.util.Map;

public interface IUserService {
    User createUser(CreateUserRequest dto) throws Exception;

    List<UserDTO> getAllUsers();

    UserDTO getUserById(Long id);

    UserDTO updateUser(Long id, UserDTO dto);

    void deleteUser(String userId);

    TokenUserResponse login(LoginRequest loginRequest);

    List<UserKeyCloakResponse> getAllUsersKeyCloak();

    Map<String, String> getUuidClient();

    String activeUser(long userId, String activeCode);

    String resendActiveCode(long userId);

    UserDTO extractUsername(String token);

    void createUserFromGoogle(String email,
                              String firstName,
                              String lastName,
                              String keycloakUserId);


    TokenUserResponse exchangeCodeToToken(String code);

    TokenUserResponse handleOAuth2Login(String code);

    void sendResetPassword(String userId, String password, String token);

    String updatePassword(CreateUserRequest request);

    String checkEmailWhenLogin(String email);

    void logout(String refreshToken);

    TokenUserResponse refresh(String refreshToken);
    void logoutAllSessions(String email);
    String extractSessionId(String accessToken);
    void forceLogoutUser(String userId);
    void logoutOldSessionsKeepLatest(String email);

}
