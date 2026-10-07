package com.thang.user.service.user;

import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.model.entity.AdminAccountAudit;
import com.thang.user.model.entity.User;
import com.thang.user.repository.AdminAccountAuditRepository;
import com.thang.user.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AdminAccountService {
    private final IUserRepository users;
    private final IUserService registration;
    private final AdminAccountAuditRepository audits;
    private final SessionService sessions;

    public record Account(String userId, String email, String fullName, String role, boolean active, boolean activationPending,
                          LocalDateTime createdAt, LocalDateTime lastLogin) {
        static Account from(User user) {
            return new Account(user.getUserId(), user.getEmail(),
                    ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                            + (user.getLastName() == null ? "" : user.getLastName())).trim(),
                    user.getRoleName(), user.isActive(), user.getCodeActive() != null,
                    user.getDateCreated(), user.getLastLogin());
        }
    }
    public record Invite(String email, String firstName, String lastName, String role) {}
    public record RoleChange(String role) {}
    public record StatusChange(boolean active) {}

    @Transactional(readOnly = true)
    public List<Account> list() { return users.findAll().stream().map(Account::from).toList(); }

    @Transactional
    public Account invite(String actor, Invite request) {
        if (request == null || request.email() == null || request.email().isBlank()
                || request.firstName() == null || request.firstName().isBlank()
                || request.lastName() == null || request.lastName().isBlank())
            throw bad("Nhập email, họ và tên");
        String role = validRole(request.role());
        // The invitee activates by email and chooses a password; no password is shared by an administrator.
        String temporaryPassword = "A1!a" + UUID.randomUUID().toString().replace("-", "");
        CreateUserRequest input = new CreateUserRequest();
        input.setEmail(request.email().trim()); input.setFirstName(request.firstName().trim());
        input.setLastName(request.lastName().trim()); input.setPassword(temporaryPassword);
        input.setConfirmPassword(temporaryPassword);
        User user = registration.createUser(input);
        user.setRoleName(role);
        user.setDateModified(LocalDateTime.now());
        users.save(user);
        audit(actor, user.getUserId(), "INVITE", null, role);
        return Account.from(user);
    }

    @Transactional
    public Account changeRole(String actor, String target, RoleChange request) {
        String role = validRole(request == null ? null : request.role());
        User user = ownTarget(actor, target);
        String previous = user.getRoleName();
        if (previous.equals(role)) return Account.from(user);
        if ("ADMIN".equals(previous) && !"ADMIN".equals(role)) keepLastAdmin();
        user.setRoleName(role); user.setDateModified(LocalDateTime.now());
        users.save(user); sessions.removeSession(target);
        audit(actor, target, "ROLE_CHANGE", previous, role);
        return Account.from(user);
    }

    @Transactional
    public Account changeStatus(String actor, String target, StatusChange request) {
        if (request == null) throw bad("Trạng thái không hợp lệ");
        User user = ownTarget(actor, target);
        if (user.isActive() == request.active()) return Account.from(user);
        if (request.active() && user.getCodeActive() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tài khoản cần xác minh email trước khi kích hoạt");
        if (!request.active() && "ADMIN".equals(user.getRoleName())) keepLastAdmin();
        String previous = user.isActive() ? "ACTIVE" : "INACTIVE";
        user.setActive(request.active()); user.setDateModified(LocalDateTime.now());
        users.save(user); sessions.removeSession(target);
        audit(actor, target, "STATUS_CHANGE", previous, request.active() ? "ACTIVE" : "INACTIVE");
        return Account.from(user);
    }

    @Transactional(readOnly = true)
    public List<AdminAccountAudit> history(String target) {
        if (!users.existsById(target)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        return audits.findByTargetUserIdOrderByCreatedAtDesc(target, PageRequest.of(0, 100));
    }

    private User ownTarget(String actor, String target) {
        if (actor.equals(target)) throw bad("Không thể tự thay đổi quyền hoặc trạng thái của mình");
        return users.lockById(target).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
    }
    private void keepLastAdmin() {
        if (users.lockActiveByRole("ADMIN").size() <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cần giữ ít nhất một Admin đang hoạt động");
    }
    private String validRole(String role) {
        if (role == null || !List.of("USER", "STAFF", "ADMIN").contains(role)) throw bad("Quyền không hợp lệ");
        return role;
    }
    private void audit(String actor, String target, String action, String oldValue, String newValue) {
        AdminAccountAudit entry = new AdminAccountAudit();
        entry.setActorUserId(actor); entry.setTargetUserId(target); entry.setAction(action);
        entry.setOldValue(oldValue); entry.setNewValue(newValue); entry.setCreatedAt(LocalDateTime.now());
        audits.save(entry);
    }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
