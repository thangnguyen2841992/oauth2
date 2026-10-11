package com.thang.user.repository;

import com.thang.user.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IUserRepository extends JpaRepository<User, String> {
    @org.springframework.data.jpa.repository.Query("select u from User u where u.isActive=true and u.roleName='USER' and (lower(u.email) like concat('%',lower(:search),'%') or lower(u.firstName) like concat('%',lower(:search),'%') or lower(u.lastName) like concat('%',lower(:search),'%')) order by u.firstName,u.userId")
    org.springframework.data.domain.Page<User> learnerSearch(String search,org.springframework.data.domain.Pageable page);
    interface DirectoryMember { String getUserId(); String getFirstName(); String getLastName(); String getRole(); }
    @org.springframework.data.jpa.repository.Query("select u.userId as userId,u.firstName as firstName,u.lastName as lastName,u.roleName as role from User u where u.isActive = true and u.roleName in ('ADMIN','STAFF') order by u.firstName,u.userId")
    java.util.List<DirectoryMember> staffDirectory();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") String id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUserId(String userId);

    boolean existsByEmail(String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.roleName = :role and u.isActive = true order by u.userId")
    java.util.List<User> lockActiveByRole(@org.springframework.data.repository.query.Param("role") String role);
}
