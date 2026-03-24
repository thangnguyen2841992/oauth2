package com.thang.user.service.user;

import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.entity.User;

import java.util.List;

public interface IUserService {
    UserDTO createUser(UserDTO dto);
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Long id);
    UserDTO updateUser(Long id, UserDTO dto);
    void deleteUser(Long id);
}
