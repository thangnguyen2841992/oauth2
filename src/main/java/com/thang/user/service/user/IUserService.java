package com.thang.user.service.user;

import com.thang.user.model.dto.*;
import com.thang.user.model.entity.User;

import java.util.List;

public interface IUserService {

    User createUser(CreateUserRequest dto);

    List<UserDTO> getAllUsers();

    User findUserByEmail(String email);

    UserDTO findUserByEmailDTO(String email);



    void deleteUser(String userId);


    String resendActiveCode(String userId);

    UserDTO extractUsername(io.jsonwebtoken.Claims claims);



    TokenUserResponse login(LoginRequest loginRequest);

    TokenUserResponse refresh(String refreshToken);



    void forceLogoutUser(String userId, String sessionId);
    String checkEmailWhenLogin(String email);

    GoogleLoginResponse  loginWithGoogle(String code);

    TokenUserResponse setupGooglePassword(
            GoogleSetupPasswordRequest request
    );
}
