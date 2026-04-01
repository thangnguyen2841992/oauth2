package com.thang.user.service.user;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
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
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PasswordEncoder passwordEncoder;


    @Value("${spring.idp.client-id}")
    @NonFinal
    private String clientId;

    @Value("${spring.idp.client-secret}")
    @NonFinal
    private String clientSecret;

    public UserServiceImpl(IUserRepository userRepository, IdentityClient identityClient, TokenCacheService tokenCacheService, ClientUuidCacheService clientUuidCacheService, RoleCacheService roleCacheService, RedisTemplate<String, String> redisTemplate, KafkaTemplate<String, Object> kafkaTemplate, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.identityClient = identityClient;
        this.tokenCacheService = tokenCacheService;
        this.clientUuidCacheService = clientUuidCacheService;
        this.roleCacheService = roleCacheService;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");


    @Override
    public UserDTO createUser(CreateUserRequest dto) {

        // 1. Validate password
        if (!isValidPassword(dto.getPassword())) {
            throw new RuntimeException("Password không hợp lệ");
        }

        // 2. Encode password
        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        // 3. Tạo activation token
        String activeCode = createActiveCode();

        // 4. Lưu DB (CHƯA ACTIVE)
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPassword(encodedPassword);
        user.setActive(false);
        user.setCodeActive(activeCode);
        user.setDateCreated(new Date());
        user.setDateModified(new Date());

        User savedUser = userRepository.save(user);

        // 5. Gửi Kafka email
        MessageResponseUser message = new MessageResponseUser();
        message.setToUserEmail(savedUser.getEmail());
        message.setToUserFullName(savedUser.getFirstName() + " " + savedUser.getLastName());
        message.setToUserId(savedUser.getId()); // hoặc id DB
        message.setActiveCode(activeCode);

        kafkaTemplate.send("send-email-active-response", message);
        log.info("Đã gửi email cho userId: ", savedUser.getUserId());
        return mapperUserToUserDTO(savedUser);
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

    @Override
    public String activeUser(long userId, String activeCode) {

        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            return "Tài khoản không tồn tại!";
        }

        User user = userOptional.get();

        if (user.isActive()) {
            return "Tài khoản đã được kích hoạt!";
        }

        if (!activeCode.equals(user.getCodeActive())) {
            return "Mã kích hoạt không hợp lệ!";
        }

        // ✅ Active DB
        user.setActive(true);
        userRepository.save(user);

        // ================================
        // ✅ TẠO USER KEYCLOAK (KHÔNG PASSWORD)
        // ================================

        var token = tokenCacheService.getClientToken();

        var response = identityClient.createNewUser(
                UserCreationParam.builder()
                        .username(user.getUsername())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .enabled(true)
                        .emailVerified(true)
                        .build(), // ❌ KHÔNG credentials
                "Bearer " + token
        );

        String keycloakUserId = extractUserId(response);

        // ================================
        // ✅ GỬI EMAIL SET PASSWORD KEYCLOAK
        // ================================

        identityClient.executeActionsEmail(
                "Bearer " + token,
                keycloakUserId,
                List.of("UPDATE_PASSWORD")
        );

        // ================================

        user.setUserId(keycloakUserId);
        userRepository.save(user);

        return "Kích hoạt thành công! Vui lòng kiểm tra email để đặt mật khẩu.";
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
        dto.setRoleName(user.getRoleName());
        return dto;
    }

    private String createActiveCode() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValidPassword(String password) {
        // Regex pattern
        String regex = "^(?=.*[0-9])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=.{8,}).*";
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(password).matches();
    }
}
