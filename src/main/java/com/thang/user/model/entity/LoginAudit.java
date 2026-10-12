package com.thang.user.model.entity;
import jakarta.persistence.*;import lombok.*;import java.time.LocalDateTime;
@Entity @Getter @Setter @Table(indexes=@Index(name="idx_login_audit_owner",columnList="user_id,created_at"))
public class LoginAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=36) private String userId;
 @Column(nullable=false,length=20) private String method;
 @Column(length=64) private String address;
 @Column(length=512) private String localAddresses;
 @Column(length=200) private String agent;
 @Column(nullable=false) private LocalDateTime createdAt;
}
