package com.thang.user.repository;

import com.thang.user.model.entity.AuthActivationOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface AuthActivationOutboxRepository extends JpaRepository<AuthActivationOutbox, String> {
    org.springframework.data.domain.Page<AuthActivationOutbox> findAllByOrderByAvailableAtAscIdAsc(Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from AuthActivationOutbox o where o.id=:id") java.util.Optional<AuthActivationOutbox> lock(String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from AuthActivationOutbox o where o.availableAt<=:now and o.claimedUntil<=:now and o.expiresAt>:now order by o.availableAt,o.id")
    List<AuthActivationOutbox> next(long now, Pageable page);

    @Modifying
    @Query("delete from AuthActivationOutbox o where o.id=:id and o.claimToken=:claim")
    int acknowledge(String id, String claim);

    @Modifying
    @Query("update AuthActivationOutbox o set o.claimToken=null,o.claimedUntil=0,o.availableAt=:nextAttempt,o.attempts=o.attempts+1 where o.id=:id and o.claimToken=:claim")
    int retry(String id, String claim, long nextAttempt);

    @Modifying
    @Query("delete from AuthActivationOutbox o where o.expiresAt<=:now")
    int removeExpired(long now);
}
