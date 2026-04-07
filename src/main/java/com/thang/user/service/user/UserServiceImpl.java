package com.thang.user.service.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.dto.LoginRequest;
import com.thang.user.model.dto.MessageResponseUser;
import com.thang.user.model.dto.UserDTO;
import com.thang.user.model.dto.identity.*;
import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import com.thang.user.repository.IdentityClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
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
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;


    @Value("${spring.idp.client-id}")
    @NonFinal
    private String clientId;

    @Value("${spring.idp.client-secret}")
    @NonFinal
    private String clientSecret;

    public UserServiceImpl(IUserRepository userRepository, IdentityClient identityClient, TokenCacheService tokenCacheService, ClientUuidCacheService clientUuidCacheService, RoleCacheService roleCacheService, RedisTemplate<String, String> redisTemplate, KafkaTemplate<String, Object> kafkaTemplate) {
        this.userRepository = userRepository;
        this.identityClient = identityClient;
        this.tokenCacheService = tokenCacheService;
        this.clientUuidCacheService = clientUuidCacheService;
        this.roleCacheService = roleCacheService;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    @Override
    public User createUser(CreateUserRequest dto) {
        String activeCode = createActiveCode();
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setDateOfBirth(formatDateFromStringToDate(dto.getDateOfBirth()));
        user.setEmail(dto.getEmail());
        user.setAddress(dto.getAddress());
        user.setActive(false);
        user.setCodeActive(activeCode);
        user.setCodeActiveExpiredAt(generateExpiredTime(1));
        user.setDateCreated(new Date());
        user.setDateModified(new Date());
        User savedUser = userRepository.save(user);
        MessageResponseUser message = new MessageResponseUser();
        message.setToUserEmail(savedUser.getEmail());
        message.setToUserFullName(savedUser.getFirstName() + " " + savedUser.getLastName());
        message.setToUserId(savedUser.getId()); // hoặc id DB
        message.setActiveCode(activeCode);

        kafkaTemplate.send("send-email-active-response", message);
        log.info("Đã gửi email cho userId: ", savedUser.getUserId());
        mapperUserToUserDTO(savedUser);
        return savedUser;
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
    public TokenUserResponse handleOAuth2Login(String code) {

        // 1. exchange code -> token
        TokenUserResponse token = this.exchangeCodeToToken(code);

        String accessToken = token.getAccess_token();

        // 2. decode JWT
        String email = extractClaim(accessToken, "email");
        String name = extractClaim(accessToken, "name");
        String sub = extractClaim(accessToken, "sub");

        // split name nếu cần
        String lastName = "";

        // 3. create/update user DB
        createUserFromGoogle(email, name, lastName, sub);

        return token;
    }

    public String extractClaim(String token, String claim) {
        try {
            String[] parts = token.split("\\.");
            String payload = new String(Base64.getDecoder().decode(parts[1]));

            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(payload, Map.class);

            return (String) map.get(claim);
        } catch (Exception e) {
            return null;
        }
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
    public String activeUser(long userId, String activeCode) {

        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            return "NOT_FOUND";
        }
        User user = userOptional.get();
        if (user.isActive()) {
            return "ALREADY_ACTIVE";
        }
        if (!activeCode.equals(user.getCodeActive())) {
            return "INVALID";
        }
        if (user.getCodeActiveExpiredAt() == null
                || user.getCodeActiveExpiredAt().before(new Date())) {
            return "EXPIRED";
        }
        var token = tokenCacheService.getClientToken();

        try {
            var response = identityClient.createNewUser(
                    UserCreationParam.builder()
                            .username(user.getUsername())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .email(user.getEmail())
                            .enabled(true)
                            .emailVerified(true)
                            .build(),
                    "Bearer " + token
            );

            String keycloakUserId = extractUserId(response);

            assignDefaultRole(keycloakUserId, "USER");



            identityClient.executeActionsEmail(
                    "Bearer " + token,
                    keycloakUserId,
                    List.of("UPDATE_PASSWORD")
            );

            user.setActive(true);
            user.setUserId(keycloakUserId);
            user.setCodeActive(null);
            user.setCodeActiveExpiredAt(null);
            userRepository.save(user);

            return "SUCCESS";

        } catch (Exception e) {
            log.error("Active user failed: ", e);
            return "Kích hoạt thất bại do lỗi hệ thống (Keycloak)";
        }
    }

    @Override
    public String resendActiveCode(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        if (user.isActive()) {
            return "Tài khoản đã kích hoạt, không cần gửi lại mã!";
        }

        String newCode = createActiveCode();

        user.setCodeActive(newCode);
        user.setCodeActiveExpiredAt(generateExpiredTime(15));
        userRepository.save(user);

        MessageResponseUser message = new MessageResponseUser();
        message.setToUserEmail(user.getEmail());
        message.setToUserFullName(user.getFirstName() + " " + user.getLastName());
        message.setToUserId(user.getId());
        message.setActiveCode(newCode);

        kafkaTemplate.send("send-email-active-response", message);

        log.info("Resend active code cho userId: {}", user.getId());

        return "Đã gửi lại mã kích hoạt!";
    }

    @Override
    public String extractUsername(String token) {
        try {
            String[] parts = token.split("\\.");

            String payload = new String(Base64.getDecoder().decode(parts[1]));

            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(payload, Map.class);

            return (String) map.get("preferred_username");
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void createUserFromGoogle(String email,
                                     String firstName,
                                     String lastName,
                                     String keycloakUserId) {

        Optional<User> userOpt = userRepository.findByEmail(email);
        String username = email.split("@")[0];
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // 🔥 update nếu chưa có userId
            if (user.getUserId() == null) {
                user.setUserId(keycloakUserId);
            }
            user.setActive(true);
            user.setProvider("GOOGLE");

            userRepository.save(user);
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);

        user.setUserId(keycloakUserId); // 🔥 QUAN TRỌNG

        user.setActive(true);
        user.setProvider("GOOGLE");

        user.setDateCreated(new Date());
        user.setDateModified(new Date());

        // 👉 default role
        user.setRoleName("USER");
        assignDefaultRole(keycloakUserId, "USER");
//        identityClient.executeActionsEmail(
//                "Bearer " + token,
//                keycloakUserId,
//                List.of("UPDATE_PASSWORD")
//        );
        userRepository.save(user);
    }

    private void assignDefaultRole(String userId, String roleName) {
        String token = tokenCacheService.getClientToken();

        GetRoleIdResponse role = roleCacheService.getRole(roleName);

        identityClient.mappingRealmRoleToUser(
                "Bearer " + token,
                userId,
                List.of(role)
        );
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

    @Override
    public TokenUserResponse exchangeCodeToToken(String code) {

        RestTemplate restTemplate = new RestTemplate();

        String url = "http://localhost:8180/realms/nihongo/protocol/openid-connect/token";

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", "japanese_app");
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", "http://localhost:8082/api/auth/callbackGoogle");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<?> request = new HttpEntity<>(body, headers);

        return restTemplate.postForObject(url, request, TokenUserResponse.class);
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
        dto.setRoleName(user.getRoleName());
        return dto;
    }

    private String createActiveCode() {
        return UUID.randomUUID().toString();
    }

    private Date generateExpiredTime(int minutes) {
        return Date.from(
                Instant.now().plusSeconds(minutes * 60L)
        );
    }
}

