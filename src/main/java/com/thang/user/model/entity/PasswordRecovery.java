package com.thang.user.model.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(indexes={@Index(name="idx_recovery_user",columnList="user_id,created_at"),@Index(name="idx_recovery_expiry",columnList="expires_at")})
public class PasswordRecovery {
 @Id @Column(length=36) private String id;
 @Column(nullable=false,length=36) private String userId;
 @Column(nullable=false,length=64) private String tokenHash;
 @Column(nullable=false,length=64) private String credentialFingerprint;
 @Column(nullable=false) private long createdAt;
 @Column(nullable=false) private long expiresAt;
 @Column(nullable=false) private long usedAt;
}
