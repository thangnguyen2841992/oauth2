package com.thang.user.repository;

import com.thang.user.model.entity.AdminAccountAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminAccountAuditRepository extends JpaRepository<AdminAccountAudit, Long> {
    List<AdminAccountAudit> findByTargetUserIdOrderByCreatedAtDesc(String targetUserId, Pageable page);
}
