package com.thang.user.repository;
import com.thang.user.model.entity.AuthMfaChallenge;import org.springframework.data.jpa.repository.*;import jakarta.persistence.LockModeType;import java.util.*;
public interface AuthMfaChallengeRepository extends JpaRepository<AuthMfaChallenge,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from AuthMfaChallenge c where c.id=:id") Optional<AuthMfaChallenge> lock(String id);
 @Modifying @Query("delete from AuthMfaChallenge c where c.expiresAt<:now") void cleanup(long now);
}
