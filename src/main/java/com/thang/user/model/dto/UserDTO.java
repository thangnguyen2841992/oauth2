package com.thang.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class UserDTO {
    private long id;
    private String userId;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String email;
    private String phoneNumber;
    private String address;

    //    @Column(unique = true, nullable = false)
//    private String plainTextDEK;
    private String dateCreated;
    private String dateModified;
}
