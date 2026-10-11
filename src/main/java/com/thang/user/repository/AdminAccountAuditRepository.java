package com.thang.user.repository;

import com.thang.user.model.entity.AdminAccountAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminAccountAuditRepository extends JpaRepository<AdminAccountAudit, Long> {
    org.springframework.data.domain.Page<AdminAccountAudit> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(java.time.LocalDateTime from,java.time.LocalDateTime to,Pageable page);
    org.springframework.data.domain.Page<AdminAccountAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable page);
    List<AdminAccountAudit> findByTargetUserIdOrderByCreatedAtDesc(String targetUserId, Pageable page);
}
