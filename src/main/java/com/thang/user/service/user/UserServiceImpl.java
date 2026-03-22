package com.thang.user.service.user;

import com.thang.user.model.User;
import com.thang.user.repository.IUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements IUserService {
    private final IUserRepository userRepository;

    public UserServiceImpl(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<User> getAllUsers() {
        return this.userRepository.findAll();
    }

    @Override
    public void addUser(User user) {
        this.userRepository.save(user);
    }

    @Override
    public User findByUsername(String username) {
        Optional<User> user = this.userRepository.findByUsername(username);
        return user.orElse(null);
    }

    @Override
    public User findByEmail(String email) {
        Optional<User> user = this.userRepository.findByEmail(email);
        return user.orElse(null);
    }
}
