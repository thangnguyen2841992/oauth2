package com.thang.user.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class User {

    @Id
    private String userId;
    private String firstName;
    private String lastName;
    private LocalDateTime dateOfBirth;
    @Column(unique = true, nullable = false)
    private String email;
    private String password;
    private String googleId;
    private String phoneNumber;
    private String address;
    private LocalDateTime dateCreated;
    private LocalDateTime dateModified;
    private LocalDateTime lastLogin;
    private String roleName;
    private boolean isActive;
    private String codeActive;
    private LocalDateTime codeActiveExpiredAt;
}