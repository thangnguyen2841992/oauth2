package com.thang.user.service.user;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.*;
import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import com.thang.user.repository.IdentityClient;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {
    private final IUserRepository userRepository;
    private final IdentityClient identityClient;
    private final TokenCacheService tokenCacheService;
    private final ClientUuidCacheService clientUuidCacheService;
    private final RoleCacheService roleCacheService;

    @Value("${spring.idp.client-id}")
    @NonFinal
    private String clientId;

    @Value("${spring.idp.client-secret}")
    @NonFinal
    private String clientSecret;

    public UserServiceImpl(IUserRepository userRepository, IdentityClient identityClient, TokenCacheService tokenCacheService, ClientUuidCacheService clientUuidCacheService, RoleCacheService roleCacheService) {
        this.userRepository = userRepository;
        this.identityClient = identityClient;
        this.tokenCacheService = tokenCacheService;
        this.clientUuidCacheService = clientUuidCacheService;
        this.roleCacheService = roleCacheService;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    @Override
    public UserDTO createUser(CreateUserRequest dto) {
        var token = tokenCacheService.getClientToken();

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
                .build(), "Bearer " + token);
        String userId = extractUserId(creationResponse);
        log.info("User created: ", userId);
        User user = new User();
        user.setUserId(userId);
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
        return mapperUserToUserDTO(newUser);
    }

    @Override
    public List<UserDTO> getAllUsers() {
        List<User> users = this.userRepository.findAll();
        List<UserDTO> dtos = new ArrayList<>();
        for (User user : users) {
            UserDTO dto = mapperUserToUserDTO(user);
            dtos.add(dto);
        }
        return dtos;
    }

    @Override
    public UserDTO getUserById(Long id) {
        Optional<User> user = this.userRepository.findById(id);
        UserDTO dto = new UserDTO();
        if (user.isPresent()) {
            dto = mapperUserToUserDTO(user.get());
        }
        return dto;
    }

    @Override
    public UserDTO updateUser(Long id, UserDTO dto) {
        return null;
    }

    @Override
    public void deleteUser(String userId) {
        Optional<User> user = this.userRepository.findByUserId(userId);
        if (user.isPresent()) {
            var token = tokenCacheService.getClientToken();
            this.identityClient.deleteUser(user.get().getUserId(), "Bearer " + token);
            this.userRepository.deleteById(user.get().getId());
        }
    }

    @Override
    public TokenUserResponse login(LoginRequest loginRequest) {
        return identityClient.login(LoginUsingKeyCloakParam.builder()
                .grant_type("password")
                .client_secret(clientSecret)
                .client_id(clientId)
                .scope("openid")
                .username(loginRequest.getUsername())
                .password(loginRequest.getPassword())
                .build());
    }

    @Override
    public List<UserKeyCloakResponse> getAllUsersKeyCloak() {
        var token = tokenCacheService.getClientToken();
        return this.identityClient.getAllUsersKeyCloak("Bearer " + token);
    }

    @Override
    public Map<String, String> getUuidClient() {
        return this.clientUuidCacheService.getAllClientUuid();
    }

    @Override
    public String getRoleId(String roleName) {
        return this.roleCacheService.getRoleId(roleName);
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

    private UserDTO mapperUserToUserDTO(User user) {
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
        return dto;
    }
}
