package com.thang.user.repository;
import com.thang.user.model.entity.LoginAudit;import org.springframework.data.jpa.repository.*;import org.springframework.data.domain.*;import java.time.LocalDateTime;
public interface LoginAuditRepository extends JpaRepository<LoginAudit,Long> {
 Page<LoginAudit> findByUserIdOrderByCreatedAtDescIdDesc(String userId,Pageable page);
 @Modifying @Query("delete from LoginAudit a where a.createdAt<:cutoff") void cleanup(LocalDateTime cutoff);
}
