package com.thang.user.repository;
import com.thang.user.model.entity.PasswordRecovery;import org.springframework.data.jpa.repository.*;import jakarta.persistence.LockModeType;import java.util.*;
public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecovery,String> {
 boolean existsByUserIdAndCreatedAtGreaterThan(String userId,long createdAt);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from PasswordRecovery r where r.id=:id") Optional<PasswordRecovery> lock(String id);
 @Modifying @Query("update PasswordRecovery r set r.usedAt=:now where r.userId=:userId and r.usedAt=0") void invalidate(String userId,long now);
 @Modifying @Query("delete from PasswordRecovery r where r.expiresAt<:cutoff") void cleanup(long cutoff);
}
