package com.thang.user.model.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter
public class AuthMfaDevice {
 @Id @Column(length=36) private String userId;
 private boolean enabled;
 @Column(length=1024) private String encryptedSecret;
 @Column(length=1024) private String pendingSecret;
 private long setupExpiresAt;
 private long lastCounter=-1;
}
