package com.thang.user.controller;

import com.thang.user.model.dto.UserDTO;
import com.thang.user.service.user.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserProfileCorsTest {
    @Test void profileDoesNotAddWildcardCorsHeadersOnRequestsForwardedByGateway() throws Exception {
        var service = mock(IUserService.class);
        var profile = new UserDTO(); profile.setEmail("owner@example.com"); profile.setFullName("Owner");
        when(service.findUserByEmailDTO("owner@example.com")).thenReturn(profile);
        // The gateway owns browser CORS; method authorization is covered by UserPrivacyTest.
        var mvc = MockMvcBuilders.standaloneSetup(new UserRestController(service)).build();
        mvc.perform(get("/api/users/findUserByEmail").param("email", "owner@example.com")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
