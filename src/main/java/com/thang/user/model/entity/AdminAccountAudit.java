package com.thang.user.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_account_audit", indexes = @Index(name = "idx_account_audit_target", columnList = "target_user_id,created_at"))
@Getter @Setter
public class AdminAccountAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 36)
    private String actorUserId;
    @Column(name = "target_user_id", nullable = false, length = 36)
    private String targetUserId;
    @Column(nullable = false, length = 24)
    private String action;
    @Column(length = 32)
    private String oldValue;
    @Column(length = 32)
    private String newValue;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
