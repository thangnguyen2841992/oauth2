package com.thang.user.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String userId;
    private String firstName;
    private String lastName;
    private Date dateOfBirth;
    private String email;
    private String password;
    private String phoneNumber;
    private String address;
    private Date dateCreated;
    private Date dateModified;
    private String roleName;
    private boolean isActive;
    private String codeActive;
    private Date codeActiveExpiredAt;
    private String provider; // LOCAL, GOOGLE
}
