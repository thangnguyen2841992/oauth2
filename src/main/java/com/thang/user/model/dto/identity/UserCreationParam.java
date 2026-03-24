package com.thang.user.model.dto.identity;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserCreationParam {
    String username;
    boolean enabled;
    String email;
    boolean emailVerified;
    String firstName;
    String lastName;
    List<Credentials> credentials;
}
