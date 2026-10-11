package com.thang.user.repository;
import com.thang.user.model.entity.AuthSession;import org.springframework.data.jpa.repository.*;import jakarta.persistence.LockModeType;import java.util.*;
public interface AuthSessionRepository extends JpaRepository<AuthSession,String> {
 interface Deadline { String getUserId();String getSid();long getExpiresAt();long getIdleUntil();String getRole();boolean getActive(); }
 @Query("select s.userId as userId,s.sid as sid,s.expiresAt as expiresAt,s.idleUntil as idleUntil,u.roleName as role,u.isActive as active from AuthSession s join User u on u.userId=s.userId where s.userId=:userId")
 Optional<Deadline> deadline(String userId);
 @Query("select s.userId as userId,s.sid as sid,s.expiresAt as expiresAt,s.idleUntil as idleUntil,u.roleName as role,u.isActive as active from AuthSession s join User u on u.userId=s.userId where s.userId in :userIds")
 List<Deadline> deadlines(Collection<String> userIds);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuthSession s where s.userId=:userId")
 Optional<AuthSession> lock(String userId);
 @Modifying @Query(value="INSERT INTO auth_session (user_id,sid,encrypted_refresh,current_hash,rotate_after,expires_at,idle_until,admin_session,version) VALUES (:userId,'','','',0,0,0,false,0) ON DUPLICATE KEY UPDATE user_id=user_id",nativeQuery=true)
 void ensure(String userId);
 @Modifying @Query("delete from AuthSession s where s.userId=:userId and (:sid is null or s.sid=:sid)")
 void revoke(String userId,String sid);
 @Modifying @Query("delete from AuthSession s where s.expiresAt<=:now or s.idleUntil<=:now")
 void cleanup(long now);
}
