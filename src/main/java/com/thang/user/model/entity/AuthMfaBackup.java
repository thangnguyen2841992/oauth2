package com.thang.user.model.entity;
import jakarta.persistence.*;import lombok.*;
@Entity @Getter @Setter @Table(indexes=@Index(name="idx_mfa_backup_user",columnList="user_id"))
public class AuthMfaBackup {
 @Id @Column(length=64) private String id;
 @Column(nullable=false,length=36) private String userId;
}
