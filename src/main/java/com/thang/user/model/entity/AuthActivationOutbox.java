package com.thang.user.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Written in the account transaction; the publisher sends only committed rows. */
@Entity @Getter @Setter
@Table(name = "auth_activation_outbox", indexes = {
        @Index(name = "idx_activation_outbox_due", columnList = "available_at,claimed_until"),
        @Index(name = "idx_activation_outbox_expiry", columnList = "expires_at")
})
public class AuthActivationOutbox {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false, length = 8192) private String encryptedPayload;
    @Column(nullable = false) private long expiresAt;
    @Column(nullable = false) private long availableAt;
    @Column(length = 36) private String claimToken;
    @Column(nullable = false) private long claimedUntil;
    @Column(nullable = false) private int attempts;
}
