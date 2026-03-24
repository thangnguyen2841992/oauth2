package com.thang.user.service.user;

import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements IUserService {
    private final IUserRepository userRepository;

    public UserServiceImpl(IUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    @Override
    public UserDTO createUser(UserDTO dto) {
        User user = new User();
//        user.setUserId(dto.getUserId());
        user.setUsername(dto.getUsername());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setAddress(dto.getAddress());
        user.setEmail(dto.getEmail());
        user.setDateOfBirth(formatDateFromStringToDate(dto.getDateOfBirth()));
        user.setDateCreated(new Date());
        user.setDateModified(new Date());
        User newUser = this.userRepository.save(user);
        dto.setId(newUser.getId());
        dto.setUserId(dto.getUserId());
        dto.setDateCreated(toIsoDateStringVn(user.getDateCreated()));
        dto.setDateModified(toIsoDateStringVn(user.getDateModified()));
        return dto;
    }

    @Override
    public List<UserDTO> getAllUsers() {
        return List.of();
    }

    @Override
    public UserDTO getUserById(Long id) {
        return null;
    }

    @Override
    public UserDTO updateUser(Long id, UserDTO dto) {
        return null;
    }

    @Override
    public void deleteUser(Long id) {

    }

    private Date formatDateFromStringToDate(String date) {
        LocalDate localDate = LocalDate.parse(date);
        Instant instant = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        return Date.from(instant);
    }

    public static String toIsoDateStringVn(Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        LocalDate localDate = instant.atZone(VN_ZONE).toLocalDate();
        return localDate.format(FORMATTER);
    }
}
