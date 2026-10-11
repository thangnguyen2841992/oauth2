package com.thang.user.model.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(name="auth_rate_bucket",indexes=@Index(name="idx_auth_rate_expiry",columnList="expiresAt"))
public class AuthRateBucket {
 @Id @Column(length=64) private String id;
 @Column(nullable=false) private double tokens;
 @Column(nullable=false) private long updatedAt;
 @Column(nullable=false) private long expiresAt;
}
