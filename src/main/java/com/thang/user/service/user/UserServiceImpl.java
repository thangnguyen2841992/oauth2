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
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {
    private final IUserRepository userRepository;
    private final IdentityClient identityClient;
    private final TokenCacheService tokenCacheService;
    private final ClientUuidCacheService clientUuidCacheService;
    private final RoleCacheService roleCacheService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PasswordEncoder passwordEncoder;


    @Value("${spring.idp.client-id}")
    @NonFinal
    private String clientId;

    @Value("${spring.idp.client-secret}")
    @NonFinal
    private String clientSecret;

    public UserServiceImpl(IUserRepository userRepository, IdentityClient identityClient, TokenCacheService tokenCacheService, ClientUuidCacheService clientUuidCacheService, RoleCacheService roleCacheService, KafkaTemplate<String, Object> kafkaTemplate, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.identityClient = identityClient;
        this.tokenCacheService = tokenCacheService;
        this.clientUuidCacheService = clientUuidCacheService;
        this.roleCacheService = roleCacheService;
        this.kafkaTemplate = kafkaTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    @Override
    public User createUser(CreateUserRequest dto) throws Exception {
        boolean isExistEmail = this.userRepository.existsByEmail(dto.getEmail());
        if (isExistEmail) {
            throw new Exception("Email đã tồn tại");
        }
        String activeCode = createActiveCode();
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setDateOfBirth(formatDateFromStringToDate(dto.getDateOfBirth()));
        user.setEmail(dto.getEmail());
        user.setAddress(dto.getAddress());
        user.setActive(false);
        user.setCodeActive(activeCode);
        user.setCodeActiveExpiredAt(generateExpiredTime(1));
        user.setDateCreated(LocalDateTime.now());
        user.setDateModified(LocalDateTime.now());
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

        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Tài khoản hoặc mật khẩu không đúng"));

        if (!user.isActive()) {
            throw new RuntimeException("Tài khoản chưa kích hoạt");
        }

        TokenUserResponse token = identityClient.login(
                LoginUsingKeyCloakParam.builder()
                        .grant_type("password")
                        .client_secret(clientSecret)
                        .client_id(clientId)
                        .scope("openid")
                        .username(loginRequest.getEmail())
                        .password(loginRequest.getPassword())
                        .build()
        );

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        return token;
    }
    @Override
    public TokenUserResponse handleOAuth2Login(String code) {

        TokenUserResponse token = this.exchangeCodeToToken(code);
        String accessToken = token.getAccess_token();
        String email = extractClaim(accessToken, "email");
        String name = extractClaim(accessToken, "name");
        String sub = extractClaim(accessToken, "sub");

        String lastName = "";
        createUserFromGoogle(email, name, lastName, sub);
        return token;
    }

    @Override
    public void sendResetPassword(String userId, String password, String token) {
        String url = "http://localhost:8180/admin/realms/nihongo/users/" + userId + "/reset-password";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token); // 👈 token admin
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("type", "password");
        body.put("value", password);
        body.put("temporary", false);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        new RestTemplate().exchange(url, HttpMethod.PUT, request, String.class);
    }

    @Override
    public String updatePassword(CreateUserRequest request) {
        if (!isValidPassword(request.getPassword())) {
            return "Password not validation";
        }
        if (!request.getConfirmPassword().equals(request.getPassword())) {
            return "Password not matches";
        }
        Optional<User> userOptional = this.userRepository.findByEmail(request.getEmail());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            var token = tokenCacheService.getClientToken();
            sendResetPassword(user.getUserId(), "thuThuy@1", token);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setDateModified(LocalDateTime.now());
            this.userRepository.save(user);
        }
        return "SUCCESS";
    }

    @Override
    public String checkEmailWhenLogin(String email) {

        String token = tokenCacheService.getClientToken();

        return identityClient.findUserByEmail("Bearer " + token, email)
                .stream()
                .findFirst()
                .map(user -> {
                    List<UserKeyCloakResponse> identities =
                            identityClient.federatedIdentity("Bearer " + token, user.getId());

                    if (identities == null || identities.isEmpty()) {
                        return "LOCAL";
                    }

                    return identities.get(0).getIdentityProvider().toUpperCase();
                })
                .orElse("NOT_FOUND");
    }

    public static boolean isValidPassword(String password) {
        // Regex pattern
        String regex = "^(?=.*[0-9])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=.{8,}).*";
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(password).matches();
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
                || user.getCodeActiveExpiredAt().isBefore(LocalDateTime.now())) {
            return "EXPIRED";
        }
        var token = tokenCacheService.getClientToken();

        try {
            var response = identityClient.createNewUser(
                    UserCreationParam.builder()
                            .username(user.getEmail())
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
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if (user.isActive()) {
            throw new RuntimeException("ALREADY_ACTIVE");
        }

        if (user.getCodeActiveExpiredAt() != null &&
                user.getCodeActiveExpiredAt().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("WAIT_EXPIRED");
        }

        // 🔥 tạo code mới
        String newCode = createActiveCode();

        user.setCodeActive(newCode);
        user.setCodeActiveExpiredAt(generateExpiredTime(1)); // 1 phút
        userRepository.save(user);

        // gửi mail
        MessageResponseUser message = new MessageResponseUser();
        message.setToUserEmail(user.getEmail());
        message.setToUserFullName(user.getFirstName() + " " + user.getLastName());
        message.setToUserId(user.getId());
        message.setActiveCode(newCode);

        kafkaTemplate.send("send-email-active-response", message);

        return "SUCCESS";
    }

    @Override
    public UserDTO extractUsername(String token) {
        try {
            String[] parts = token.split("\\.");

            String payload = new String(Base64.getDecoder().decode(parts[1]));

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(payload, Map.class);
            UserDTO newUserDto = new UserDTO();
            newUserDto.setEmail((String) map.get("email"));
            newUserDto.setFullName((String) map.get("name"));
            Map<String, Object> realmAccess = (Map<String, Object>) map.get("realm_access");

            if (realmAccess != null) {
                var roles = (List<String>) realmAccess.get("roles");

                List<String> priority = List.of("ADMIN", "STAFF", "USER");

                if (roles != null) {
                    for (String p : priority) {
                        if (roles.contains(p)) {
                            newUserDto.setRoleName(p);
                            break;
                        }
                    }
                }
            }
            return newUserDto;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void logout(String refreshToken) {

        RestTemplate rest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", "japanese_app");
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        rest.postForEntity(
                "http://localhost:8180/realms/nihongo/protocol/openid-connect/logout",
                request,
                String.class
        );
    }

    @Override
    public TokenUserResponse refresh(String refreshToken) {
        RefreshTokenParam param = new RefreshTokenParam();
        param.setClient_id("japanese_app");
        param.setClient_secret(clientSecret); // nếu có
        param.setRefresh_token(refreshToken);

        return identityClient.refresh(param);
    }

    @Override
    public void createUserFromGoogle(String email,
                                     String firstName,
                                     String lastName,
                                     String keycloakUserId) {

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
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
        user.setFirstName(firstName);
        user.setLastName(lastName);

        user.setUserId(keycloakUserId);

        user.setActive(true);
        user.setProvider("GOOGLE");

        user.setDateCreated(LocalDateTime.now());
        user.setDateModified(LocalDateTime.now());

        user.setRoleName("USER");
        assignDefaultRole(keycloakUserId, "USER");
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

    private LocalDateTime formatDateFromStringToDate(String date) {
        return LocalDate.parse(date)
                .atStartOfDay();
    }

    public static String toIsoDateStringVn(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime
                .atZone(VN_ZONE)
                .toLocalDate()
                .format(FORMATTER);
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
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setFullName(user.getFirstName() + " " + user.getLastName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAddress(user.getAddress());
        dto.setEmail(user.getEmail());
        dto.setId(user.getId());
        dto.setDateCreated(toIsoDateStringVn(user.getDateCreated()));
        dto.setDateModified(toIsoDateStringVn(user.getDateModified()));
        dto.setUserId(user.getUserId());
        dto.setDateOfBirth(toIsoDateStringVn(user.getDateOfBirth()));
        dto.setLastLogin(toIsoDateStringVn(user.getLastLogin()));
        dto.setRoleName(user.getRoleName());
        if (user.isActive()) {
            dto.setActiveStatus("Đã kích hoạt");
        } else {
            dto.setActiveStatus("Chưa kích hoạt");
        }
        return dto;
    }

    private String createActiveCode() {
        return UUID.randomUUID().toString();
    }

    private LocalDateTime generateExpiredTime(int minutes) {
        return LocalDateTime.now().plusMinutes(minutes);
    }
}

