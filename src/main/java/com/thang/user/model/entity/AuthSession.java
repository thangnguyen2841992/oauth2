package com.thang.user.model.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(name="auth_session",indexes={@Index(name="idx_auth_session_expiry",columnList="expiresAt"),@Index(name="idx_auth_session_idle",columnList="idleUntil")})
public class AuthSession {
 @Id @Column(name="user_id",length=36) private String userId;
 @Column(nullable=false,length=36) private String sid;
 @Column(nullable=false,length=8192) private String encryptedRefresh;
 @Column(nullable=false,length=64) private String currentHash;
 @Column(length=64) private String previousHash;
 @Column(nullable=false) private long rotateAfter;
 @Column(nullable=false) private long expiresAt;
 @Column(nullable=false) private long idleUntil;
 @Column(nullable=false) private boolean adminSession;
 @Version private long version;
}
