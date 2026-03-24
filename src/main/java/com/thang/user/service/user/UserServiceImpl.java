package com.thang.user.service.user;

import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.Credentials;
import com.thang.user.model.dto.identity.TokenExchangeParam;
import com.thang.user.model.dto.identity.UserCreationParam;
import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import com.thang.user.repository.IdentityClient;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {
    private final IUserRepository userRepository;
    private final IdentityClient identityClient;

    @Value("${spring.idp.client-id}")
    @NonFinal
    private String clientId;

    @Value("${spring.idp.client-secret}")
    @NonFinal
    private String clientSecret;

    public UserServiceImpl(IUserRepository userRepository, IdentityClient identityClient) {
        this.userRepository = userRepository;
        this.identityClient = identityClient;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    @Override
    public UserDTO createUser(UserDTO dto) {
        var token = identityClient.exchangeClientToken(TokenExchangeParam.builder().grantType("client_credentials")
                .clientSecret(clientSecret)
                .clientId(clientId)
                .scope("openid")
                .build());

        log.info("Token info: ", token);
        var creationResponse = identityClient.createNewUser(UserCreationParam.builder()
                .username(dto.getUsername())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .emailVerified(false)
                .enabled(true)
                .credentials(List.of(Credentials.builder()
                        .value(dto.getPassword())
                        .type("password")
                        .temporary(false)
                        .build()))
                .build(), "Bearer " + token.getAccessToken());
        String userId = extractUserId(creationResponse);
        log.info("User created: ", userId);
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
        dto.setUserId(userId);
        dto.setDateCreated(toIsoDateStringVn(user.getDateCreated()));
        dto.setDateModified(toIsoDateStringVn(user.getDateModified()));
        return dto;
    }

    @Override
    public List<UserDTO> getAllUsers() {
        List<User> users = this.userRepository.findAll();
        List<UserDTO> dtos = new ArrayList<>();
        for (User user : users) {
            UserDTO dto = new UserDTO();
            dto.setUsername(user.getUsername());
            dto.setFirstName(user.getFirstName());
            dto.setLastName(user.getLastName());
            dto.setPhoneNumber(user.getPhoneNumber());
            dto.setAddress(user.getAddress());
            dto.setEmail(user.getEmail());
            dto.setId(user.getId());
            dto.setDateCreated(toIsoDateStringVn(user.getDateCreated()));
            dto.setDateModified(toIsoDateStringVn(user.getDateModified()));
            dto.setUserId(user.getUserId());
            dto.setDateOfBirth(toIsoDateStringVn(user.getDateOfBirth()));
            dtos.add(dto);
        }
        return dtos;
    }

    @Override
    public UserDTO getUserById(Long id) {
        Optional<User> user = this.userRepository.findById(id);
        UserDTO dto = new UserDTO();
        if (user.isPresent()) {
            dto.setUsername(user.get().getUsername());
            dto.setFirstName(user.get().getFirstName());
            dto.setLastName(user.get().getLastName());
            dto.setPhoneNumber(user.get().getPhoneNumber());
            dto.setAddress(user.get().getAddress());
            dto.setEmail(user.get().getEmail());
            dto.setId(user.get().getId());
            dto.setDateCreated(toIsoDateStringVn(user.get().getDateCreated()));
            dto.setDateModified(toIsoDateStringVn(user.get().getDateModified()));
            dto.setUserId(user.get().getUserId());
            dto.setDateOfBirth(toIsoDateStringVn(user.get().getDateOfBirth()));
        }
        return dto;
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

    private String extractUserId(ResponseEntity<?> response) {
        List<String> locations = response.getHeaders().get("Location");
        if (locations == null || locations.isEmpty()) {
            throw new IllegalStateException("Location header missing in the response");
        }
        String location = locations.get(0);
        String[] split = location.split("/");
        return split[split.length - 1];
    }
}
