package com.thang.user.service.user;

import com.thang.user.model.entity.User;
import com.thang.user.model.dto.CreateUserRequest;
import com.thang.user.repository.AdminAccountAuditRepository;
import com.thang.user.repository.IUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class AdminAccountServiceTest {
    IUserRepository users = mock(IUserRepository.class);
    IUserService registration = mock(IUserService.class);
    AdminAccountAuditRepository audits = mock(AdminAccountAuditRepository.class);
    SessionService sessions = mock(SessionService.class);
    AdminAccountService service = new AdminAccountService(users, registration, audits, sessions);

    @Test void cannotRemoveLastActiveAdmin() {
        User target = new User(); target.setUserId("admin-b"); target.setRoleName("ADMIN"); target.setActive(true);
        when(users.lockById("admin-b")).thenReturn(Optional.of(target));
        when(users.lockActiveByRole("ADMIN")).thenReturn(List.of(target));
        assertThrows(ResponseStatusException.class,
                () -> service.changeRole("admin-a", "admin-b", new AdminAccountService.RoleChange("STAFF")));
        assertEquals("ADMIN", target.getRoleName());
        verify(sessions, never()).removeSession(anyString());
    }

    @Test void cannotBypassEmailActivationOrChangeOwnStatus() {
        User target = new User(); target.setUserId("invitee"); target.setRoleName("STAFF");
        target.setCodeActive("pending-code");
        when(users.lockById("invitee")).thenReturn(Optional.of(target));
        assertThrows(ResponseStatusException.class,
                () -> service.changeStatus("admin", "invitee", new AdminAccountService.StatusChange(true)));
        assertThrows(ResponseStatusException.class,
                () -> service.changeStatus("invitee", "invitee", new AdminAccountService.StatusChange(true)));
        verify(sessions, never()).removeSession(anyString());
    }

    @Test void inviteUsesEmailActivationAndAssignsRequestedRole() {
        User created = new User(); created.setUserId("new-user"); created.setEmail("staff@example.com");
        created.setFirstName("An"); created.setLastName("Nguyễn"); created.setCodeActive("mail-code");
        when(registration.createUser(any(CreateUserRequest.class))).thenReturn(created);
        var result = service.invite("admin", new AdminAccountService.Invite("staff@example.com", "An", "Nguyễn", "STAFF"));
        assertEquals("STAFF", result.role());
        assertTrue(result.activationPending());
        ArgumentCaptor<CreateUserRequest> request = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(registration).createUser(request.capture());
        assertEquals(request.getValue().getPassword(), request.getValue().getConfirmPassword());
        verify(users).save(created);
    }
}
