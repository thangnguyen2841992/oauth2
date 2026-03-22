package com.thang.user.service.user;

import com.thang.user.model.User;

import java.util.List;
import java.util.Optional;

public interface IUserService {
    List<User> getAllUsers();
    void addUser(User user);
    User findByUsername(String username);
    User findByEmail(String email);
}
