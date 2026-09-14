package com.thang.user.service.user;

import com.thang.user.model.dto.*;
import com.thang.user.model.entity.User;
import com.thang.user.repository.IUserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final IUserRepository userRepository;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final PasswordEncoder passwordEncoder;

    private final SimpMessagingTemplate messagingTemplate;

    private final SessionService sessionService;

    private final TokenService tokenService;

    private final JwtService jwtService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String DEFAULT_ROLE = "USER";

    private final GoogleOAuthService googleOAuthService;

    // =========================================================
    // CREATE USER
    // =========================================================

    @Override
    @Transactional
    public User createUser(CreateUserRequest dto) throws Exception {

        if (dto == null) {
            throw new Exception("Thông tin user không được để trống");
        }

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {

            throw new Exception("Email không được để trống");
        }

        boolean isExistEmail = userRepository.existsByEmail(dto.getEmail());

        if (isExistEmail) {
            throw new Exception("Email đã tồn tại");
        }

        if (dto.getPassword() == null || dto.getPassword().isBlank()) {

            throw new Exception("Password không được để trống");
        }

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {

            throw new Exception("Password không khớp");
        }

        if (isInvalidPassword(dto.getPassword())) {

            throw new Exception("Password phải có ít nhất 8 ký tự, " + "bao gồm chữ hoa, số và ký tự đặc biệt");
        }

        String activeCode = createActiveCode();

        User user = new User();

        user.setUserId(UUID.randomUUID().toString());

        user.setFirstName(dto.getFirstName());

        user.setLastName(dto.getLastName());

        user.setDateOfBirth(formatDateFromStringToDate(dto.getDateOfBirth()));

        user.setEmail(dto.getEmail());

        user.setAddress(dto.getAddress());

        user.setActive(false);

        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        user.setCodeActive(activeCode);

        user.setCodeActiveExpiredAt(generateExpiredTime(1));

        user.setDateCreated(LocalDateTime.now());

        user.setDateModified(LocalDateTime.now());

        user.setRoleName(DEFAULT_ROLE);

        User savedUser = userRepository.save(user);

        // =====================================================
        // GỬI EMAIL KÍCH HOẠT
        // =====================================================

        MessageResponseUser message = new MessageResponseUser();

        message.setToUserId(savedUser.getUserId());

        message.setToUserEmail(savedUser.getEmail());

        message.setToUserFullName(savedUser.getFirstName() + " " + savedUser.getLastName());

        message.setActiveCode(activeCode);

        kafkaTemplate.send("send-email-active-response", message);

        log.info("Đã gửi email kích hoạt cho userId={}", savedUser.getUserId());

        return savedUser;
    }

    // =========================================================
    // GET ALL USERS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {

        List<User> users = userRepository.findAll();

        List<UserDTO> dtos = new ArrayList<>();

        for (User user : users) {

            dtos.add(mapperUserToUserDTO(user));
        }

        return dtos;
    }

    // =========================================================
    // FIND USER BY EMAIL
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public User findUserByEmail(String email) {

        if (email == null || email.isBlank()) {
            return null;
        }

        return userRepository.findByEmail(email).orElse(null);
    }

    // =========================================================
    // FIND USER BY EMAIL DTO
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public UserDTO findUserByEmailDTO(String email) {

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            return null;
        }

        return mapperUserToUserDTO(user);
    }


    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserDTO dto) {

        if (dto == null) {
            return null;
        }

        Optional<User> optionalUser = userRepository.findById(id);

        if (optionalUser.isEmpty()) {
            return null;
        }

        User user = optionalUser.get();

        if (dto.getFirstName() != null) {

            user.setFirstName(dto.getFirstName());
        }

        if (dto.getLastName() != null) {

            user.setLastName(dto.getLastName());
        }

        if (dto.getPhoneNumber() != null) {

            user.setPhoneNumber(dto.getPhoneNumber());
        }

        if (dto.getAddress() != null) {

            user.setAddress(dto.getAddress());
        }

        user.setDateModified(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        return mapperUserToUserDTO(savedUser);
    }

    // =========================================================
    // DELETE USER
    // =========================================================

    @Override
    @Transactional
    public void deleteUser(String userId) {

        if (userId == null || userId.isBlank()) {

            return;
        }

        User user = userRepository.findByUserId(userId).orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        // Xóa session Redis trước
        sessionService.removeSession(user.getUserId());

        // Xóa user DB
        userRepository.delete(user);

        log.info("Deleted user: userId={}", userId);
    }

    // =========================================================
    // LOGIN
    // =========================================================

    @Override
    @Transactional
    public TokenUserResponse login(LoginRequest loginRequest) {

        if (loginRequest == null || loginRequest.getEmail() == null || loginRequest.getEmail().isBlank()) {

            throw new RuntimeException("Email không được để trống");
        }

        if (loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {

            throw new RuntimeException("Password không được để trống");
        }

        User user = userRepository.findByEmail(loginRequest.getEmail()).orElseThrow(() -> new RuntimeException("Tài khoản hoặc mật khẩu không đúng"));

        if (!user.isActive()) {

            throw new RuntimeException("Tài khoản chưa kích hoạt");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {

            throw new RuntimeException("Tài khoản hoặc mật khẩu không đúng");
        }

        // =====================================================
        // TẠO SESSION MỚI
        // =====================================================

        String newSessionId = UUID.randomUUID().toString();

        if (newSessionId == null || newSessionId.isBlank()) {

            newSessionId = UUID.randomUUID().toString();
        }

        // =====================================================
        // LẤY SESSION CŨ
        // =====================================================

        String oldSessionId = sessionService.getSession(user.getUserId());

        // =====================================================
        // FORCE LOGOUT SESSION CŨ
        // =====================================================

        if (oldSessionId != null && !oldSessionId.equals(newSessionId)) {

            forceLogoutUser(user.getUserId(), oldSessionId);
        }

        // =====================================================
        // SAVE SESSION MỚI
        // =====================================================

        sessionService.saveSession(user.getUserId(), newSessionId);

        // =====================================================
        // UPDATE LAST LOGIN
        // =====================================================

        user.setLastLogin(LocalDateTime.now());

        user.setDateModified(LocalDateTime.now());

        userRepository.save(user);

        // =====================================================
        // GENERATE JWT
        // =====================================================

        return tokenService.generateToken(user, newSessionId);
    }

    // =========================================================
    // REFRESH TOKEN
    // =========================================================

    @Override
    @Transactional
    public TokenUserResponse refresh(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {

            throw new RuntimeException("Refresh token không được để trống");
        }
        return tokenService.refreshToken(refreshToken);
    }

    // =========================================================
    // UPDATE PASSWORD
    // =========================================================

    @Override
    @Transactional
    public String updatePassword(CreateUserRequest request) {

        if (request == null) {
            return "INVALID_REQUEST";
        }

        if (isInvalidPassword(request.getPassword())) {

            return "Password not validation";
        }

        if (!Objects.equals(request.getPassword(), request.getConfirmPassword())) {

            return "Password not matches";
        }

        if (request.getEmail() == null || request.getEmail().isBlank()) {

            return "EMAIL_REQUIRED";
        }

        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if (userOptional.isEmpty()) {
            return "USER_NOT_FOUND";
        }

        User user = userOptional.get();

        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setDateModified(LocalDateTime.now());

        userRepository.save(user);

        /*
         * Password thay đổi => invalidate session hiện tại.
         *
         * User sẽ phải login lại.
         */
        sessionService.removeSession(user.getUserId());

        log.info("Password updated and session invalidated: userId={}", user.getUserId());

        return "SUCCESS";
    }

    // =========================================================
    // PASSWORD VALIDATION
    // =========================================================

    private static boolean isInvalidPassword(String password) {

        if (password == null) {
            return true;
        }
        String regex = "^(?=.*[0-9])" + "(?=.*[A-Z])" + "(?=.*[@#$%^&+=!])" + "(?=.{8,}).*$";

        return !Pattern.compile(regex).matcher(password).matches();
    }

    @Override
    public UserDTO extractUsername(String token) {
        try {
            if (token == null || token.isBlank()) {

                return null;
            }
            /*
             * Verify JWT trước khi lấy thông tin.
             */
            Claims claims = jwtService.parseAndValidate(token);
            UserDTO dto = new UserDTO();

            // =================================================
            // USER ID
            // =================================================

            String userId = claims.getSubject();

            if (userId != null) {

                dto.setUserId(userId);
            }

            // =================================================
            // EMAIL
            // =================================================

            String email = claims.get("email", String.class);

            if (email != null) {

                dto.setEmail(email);
            }

            // =================================================
            // NAME
            // =================================================

            String name = claims.get("name", String.class);

            if (name != null) {

                dto.setFullName(name);
            }

            // =================================================
            // ROLE
            // =================================================

            Object rolesObject = claims.get("roles");

            if (rolesObject instanceof List<?> roles) {

                List<String> priority = List.of("ADMIN", "STAFF", "USER");

                for (String role : priority) {

                    if (roles.contains(role)) {

                        dto.setRoleName(role);

                        break;
                    }
                }
            }

            return dto;

        } catch (Exception e) {

            log.error("Cannot extract user from JWT", e);

            return null;
        }
    }

    // =========================================================
    // ACTIVE USER
    // =========================================================

    @Override
    @Transactional
    public String activeUser(String userId, String activeCode) {

        Optional<User> userOptional = userRepository.findByUserId(userId);

        if (userOptional.isEmpty()) {

            return "NOT_FOUND";
        }

        User user = userOptional.get();

        if (user.isActive()) {

            return "ALREADY_ACTIVE";
        }

        if (activeCode == null || !activeCode.equals(user.getCodeActive())) {

            return "INVALID";
        }

        if (user.getCodeActiveExpiredAt() == null || user.getCodeActiveExpiredAt().isBefore(LocalDateTime.now())) {

            return "EXPIRED";
        }

        try {

            user.setActive(true);

            user.setCodeActive(null);

            user.setCodeActiveExpiredAt(null);

            user.setDateModified(LocalDateTime.now());

            userRepository.save(user);

            log.info("Kích hoạt tài khoản thành công: userId={}, email={}", user.getUserId(), user.getEmail());

            return "SUCCESS";

        } catch (Exception e) {

            log.error("Active user failed: userId={}", userId, e);

            return "Kích hoạt thất bại do lỗi hệ thống";
        }
    }

    // =========================================================
    // RESEND ACTIVE CODE
    // =========================================================

    @Override
    @Transactional
    public String resendActiveCode(long userId) {

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));

        if (user.isActive()) {

            throw new RuntimeException("ALREADY_ACTIVE");
        }

        if (user.getCodeActiveExpiredAt() != null && user.getCodeActiveExpiredAt().isAfter(LocalDateTime.now())) {

            throw new RuntimeException("WAIT_EXPIRED");
        }

        String newCode = createActiveCode();

        user.setCodeActive(newCode);

        user.setCodeActiveExpiredAt(generateExpiredTime(1));

        userRepository.save(user);

        MessageResponseUser message = new MessageResponseUser();

        message.setToUserId(user.getUserId());

        message.setToUserEmail(user.getEmail());

        message.setToUserFullName(user.getFirstName() + " " + user.getLastName());

        message.setActiveCode(newCode);

        kafkaTemplate.send("send-email-active-response", message);

        log.info("Resend activation code: userId={}", user.getUserId());

        return "SUCCESS";
    }

    // =========================================================
    // LOGOUT
    // =========================================================

    @Override
    @Transactional
    public void logout(String userId) {

        if (userId == null || userId.isBlank()) {

            return;
        }

        sessionService.removeSession(userId);

        log.info("User logged out: userId={}", userId);
    }

    // =========================================================
    // LOGOUT ALL SESSIONS
    // =========================================================

    @Override
    @Transactional
    public void logoutAllSessions(String userId) {

        if (userId == null || userId.isBlank()) {

            return;
        }

        /*
         * Hiện tại Redis chỉ lưu:
         *
         * user:session:{userId}
         *
         * nên mỗi user chỉ có một session.
         *
         * Xóa session này = logout toàn bộ.
         */

        sessionService.removeSession(userId);

        log.info("Logout all sessions: userId={}", userId);
    }

    // =========================================================
    // FORCE LOGOUT
    // =========================================================

    @Override
    public void forceLogoutUser(String userId, String oldSessionId) {

        if (userId == null || userId.isBlank() || oldSessionId == null || oldSessionId.isBlank()) {

            return;
        }

        Map<String, String> payload = new HashMap<>();

        payload.put("type", "FORCE_LOGOUT");

        payload.put("sessionId", oldSessionId);

        messagingTemplate.convertAndSendToUser(userId, "/queue/logout", payload);

        log.info("Force logout user: userId={}, sessionId={}", userId, oldSessionId);
    }

    @Override
    public String checkEmailWhenLogin(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("USER_NOT_EXIST");
        }
        User user = userOptional.get();
        if (!user.isActive()) {
            throw new RuntimeException("USER_NOT_ACTIVE");
        }
//        if (user.getGoogleId() != null && !user.getGoogleId().isBlank()) {
//            return "GOOGLE";
//        }
        return "LOCAL";
    }

    private LocalDateTime formatDateFromStringToDate(String date) {

        if (date == null || date.isBlank()) {

            return null;
        }

        return LocalDate.parse(date).atStartOfDay();
    }

    public static String toIsoDateStringVn(LocalDateTime dateTime) {

        if (dateTime == null) {
            return null;
        }

        return dateTime.atZone(VN_ZONE).toLocalDate().format(FORMATTER);
    }

    private UserDTO mapperUserToUserDTO(User user) {

        UserDTO dto = new UserDTO();

        dto.setFirstName(user.getFirstName());

        dto.setLastName(user.getLastName());

        dto.setFullName(user.getFirstName() + " " + user.getLastName());

        dto.setPhoneNumber(user.getPhoneNumber());

        dto.setAddress(user.getAddress());

        dto.setEmail(user.getEmail());

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

    // =========================================================
    // HELPER - ACTIVE CODE
    // =========================================================

    private String createActiveCode() {

        return UUID.randomUUID().toString();
    }

    private LocalDateTime generateExpiredTime(int minutes) {

        return LocalDateTime.now().plusMinutes(minutes);
    }


    @Override
    public String extractSessionId(String accessToken) {

        Claims claims = jwtService.parseAndValidate(accessToken);

        return claims.get("sessionId", String.class);
    }

    @Override
    @Transactional
    public GoogleLoginResponse loginWithGoogle(String code) {

        // =========================================================
        // GOOGLE USER
        // =========================================================

        GoogleUserInfo googleUser = googleOAuthService.getGoogleUser(code);

        String email = googleUser.getEmail().trim().toLowerCase();

        String googleId = googleUser.getSub();


        // =========================================================
        // CHECK EMAIL
        // =========================================================

        Optional<User> optionalUser = userRepository.findByEmail(email);


        // =========================================================
        // ĐÃ CÓ ACCOUNT
        // =========================================================

        if (optionalUser.isPresent()) {

            User user = optionalUser.get();

            // -----------------------------------------------------
            // Google ID đã tồn tại
            // -----------------------------------------------------

            if (user.getGoogleId() != null && !user.getGoogleId().isBlank()) {

                if (!user.getGoogleId().equals(googleId)) {

                    throw new RuntimeException("Google account không khớp");
                }
            }

            // -----------------------------------------------------
            // Local account -> link Google
            // -----------------------------------------------------

            if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {

                user.setGoogleId(googleId);

                user.setDateModified(LocalDateTime.now());

                userRepository.save(user);
            }

            // -----------------------------------------------------
            // Active
            // -----------------------------------------------------

            if (!user.isActive()) {

                throw new RuntimeException("Tài khoản chưa kích hoạt");
            }

            // -----------------------------------------------------
            // Login
            // -----------------------------------------------------

            TokenUserResponse token = createLoginToken(user);

            return GoogleLoginResponse.builder()

                    .status("LOGIN")

                    .token(token)

                    .email(user.getEmail())

                    .firstName(user.getFirstName())

                    .lastName(user.getLastName())

                    .build();
        }


        // =========================================================
        // CHƯA CÓ ACCOUNT
        // =========================================================

        String setupToken = jwtService.generateGoogleSetupToken(email, googleId, googleUser.getGiven_name(), googleUser.getFamily_name());

        return GoogleLoginResponse.builder()

                .status("SET_PASSWORD")

                .setupToken(setupToken)

                .email(email)

                .firstName(googleUser.getGiven_name())

                .lastName(googleUser.getFamily_name())

                .build();
    }

    @Override
    @Transactional
    public TokenUserResponse setupGooglePassword(GoogleSetupPasswordRequest request) {

        // =========================================================
        // VALIDATE REQUEST
        // =========================================================

        if (request == null) {

            throw new RuntimeException("Request không hợp lệ");
        }

        if (request.getSetupToken() == null || request.getSetupToken().isBlank()) {

            throw new RuntimeException("Google setup token không được để trống");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {

            throw new RuntimeException("Password không được để trống");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {

            throw new RuntimeException("Password không khớp");
        }

        if (isInvalidPassword(request.getPassword())) {

            throw new RuntimeException("Password phải có ít nhất 8 ký tự, " + "bao gồm chữ hoa, số và ký tự đặc biệt");
        }


        // =========================================================
        // VALIDATE GOOGLE SETUP TOKEN
        // =========================================================

        Claims claims = jwtService.parseGoogleSetupToken(request.getSetupToken());


        String email = claims.get("email", String.class);

        String googleId = claims.get("googleId", String.class);

        String firstName = claims.get("firstName", String.class);

        String lastName = claims.get("lastName", String.class);


        if (email == null || email.isBlank()) {

            throw new RuntimeException("Google email không hợp lệ");
        }

        if (googleId == null || googleId.isBlank()) {

            throw new RuntimeException("Google ID không hợp lệ");
        }


        // =========================================================
        // CHECK RACE CONDITION
        // =========================================================

        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isPresent()) {

            throw new RuntimeException("Email đã được đăng ký");
        }


        // =========================================================
        // CREATE USER
        // =========================================================

        User user = new User();

        user.setUserId(UUID.randomUUID().toString());

        user.setFirstName(firstName);

        user.setLastName(lastName);

        user.setEmail(email);

        user.setGoogleId(googleId);


        // =========================================================
        // PASSWORD
        // =========================================================

        user.setPassword(passwordEncoder.encode(request.getPassword()));


        // =========================================================
        // DEFAULT
        // =========================================================

        user.setRoleName(DEFAULT_ROLE);

        /*
         * Google đã xác minh email.
         *
         * Không cần gửi activation email lần nữa.
         */

        user.setActive(true);

        user.setCodeActive(null);

        user.setCodeActiveExpiredAt(null);


        // =========================================================
        // DATE
        // =========================================================

        LocalDateTime now = LocalDateTime.now();

        user.setDateCreated(now);

        user.setDateModified(now);

        user.setLastLogin(now);


        // =========================================================
        // SAVE
        // =========================================================

        User savedUser = userRepository.save(user);


        log.info("Created Google user: userId={}, email={}", savedUser.getUserId(), savedUser.getEmail());


        // =========================================================
        // LOGIN
        // =========================================================

        return createLoginToken(savedUser);
    }

    private TokenUserResponse createLoginToken(User user) {

        // =========================================================
        // SESSION MỚI
        // =========================================================

        String newSessionId = UUID.randomUUID().toString();


        // =========================================================
        // SESSION CŨ
        // =========================================================

        String oldSessionId = sessionService.getSession(user.getUserId());


        // =========================================================
        // FORCE LOGOUT SESSION CŨ
        // =========================================================

        if (oldSessionId != null && !oldSessionId.equals(newSessionId)) {

            forceLogoutUser(user.getUserId(), oldSessionId);
        }


        // =========================================================
        // SAVE SESSION
        // =========================================================

        sessionService.saveSession(user.getUserId(), newSessionId);


        // =========================================================
        // LAST LOGIN
        // =========================================================

        user.setLastLogin(LocalDateTime.now());

        user.setDateModified(LocalDateTime.now());

        userRepository.save(user);


        // =========================================================
        // JWT
        // =========================================================

        return tokenService.generateToken(user, newSessionId);
    }
}