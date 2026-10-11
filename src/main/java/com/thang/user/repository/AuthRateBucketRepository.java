package com.thang.user.repository;
import com.thang.user.model.entity.AuthRateBucket;import org.springframework.data.jpa.repository.*;import jakarta.persistence.LockModeType;import java.util.Optional;
public interface AuthRateBucketRepository extends JpaRepository<AuthRateBucket,String> {
 @Modifying @Query(value="INSERT INTO auth_rate_bucket (id,tokens,updated_at,expires_at) VALUES (:id,:capacity,:now,:expires) ON DUPLICATE KEY UPDATE id=id",nativeQuery=true)void ensure(String id,double capacity,long now,long expires);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from AuthRateBucket b where b.id=:id")Optional<AuthRateBucket> lock(String id);
 @Modifying @Query("delete from AuthRateBucket b where b.expiresAt<:now")void cleanup(long now);
}
