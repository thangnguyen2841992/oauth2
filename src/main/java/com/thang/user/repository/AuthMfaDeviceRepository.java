package com.thang.user.repository;
import com.thang.user.model.entity.AuthMfaDevice;import org.springframework.data.jpa.repository.*;import jakarta.persistence.LockModeType;import java.util.*;
public interface AuthMfaDeviceRepository extends JpaRepository<AuthMfaDevice,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from AuthMfaDevice d where d.userId=:userId") Optional<AuthMfaDevice> lock(String userId);
}
