package com.thang.user.repository;
import com.thang.user.model.entity.AuthMfaBackup;import org.springframework.data.jpa.repository.*;
public interface AuthMfaBackupRepository extends JpaRepository<AuthMfaBackup,String> {
 long countByUserId(String userId);
 @Modifying @Query("delete from AuthMfaBackup b where b.userId=:userId") void clear(String userId);
}
