package com.thang.user.model.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(indexes=@Index(name="idx_mfa_challenge_expiry",columnList="expires_at"))
public class AuthMfaChallenge {
 @Id @Column(length=64) private String id;
 @Column(nullable=false,length=36) private String userId;
 @Column(nullable=false,length=64) private String credentialFingerprint;
 @Column(name="role_name",nullable=false,length=20) private String role;
 private long expiresAt;
 private int attempts;
}
