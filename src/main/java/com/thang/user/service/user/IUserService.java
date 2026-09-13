package com.thang.user.service.user;

import com.thang.user.model.dto.*;
import com.thang.user.model.entity.User;

import java.util.List;

public interface IUserService {

    User createUser(CreateUserRequest dto) throws Exception;

    List<UserDTO> getAllUsers();

    User findUserByEmail(String email);

    UserDTO findUserByEmailDTO(String email);


    UserDTO updateUser(Long id, UserDTO dto);

    void deleteUser(String userId);

    String activeUser(String userId, String activeCode);

    String resendActiveCode(long userId);

    UserDTO extractUsername(String token);

    String updatePassword(CreateUserRequest request);

    TokenUserResponse login(LoginRequest loginRequest);

    TokenUserResponse refresh(String refreshToken);

    void logout(String userId);

    void logoutAllSessions(String userId);

    void forceLogoutUser(String userId, String sessionId);
    String checkEmailWhenLogin(String email);
    String extractSessionId(String accessToken);
}