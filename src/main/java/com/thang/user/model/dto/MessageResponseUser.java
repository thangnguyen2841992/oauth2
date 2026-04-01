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

    private long toUserId;
    private String activeCode;


}