package com.thang.user.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class MessageResponseUser {
    private String toUserEmail;
    private String toUserName;
    private String toUserFullName;
    private String toUserId;
    private String activeCode;
    private String purpose;
    private String recoveryId;
    private String recoveryToken;
    public MessageResponseUser(String email,String name,String fullName,String userId,String activeCode) {
        this.toUserEmail=email;this.toUserName=name;this.toUserFullName=fullName;this.toUserId=userId;this.activeCode=activeCode;
    }


}
