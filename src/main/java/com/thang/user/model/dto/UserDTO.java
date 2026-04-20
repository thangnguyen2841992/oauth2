package com.thang.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserDTO {
    private long id;
    private String userId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String dateOfBirth;
    private String email;
    private String phoneNumber;
    private String address;
    private String roleName;
    private String dateCreated;
    private String dateModified;
    private String lastLogin;
    private String activeStatus;
}
