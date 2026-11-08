package com.daomanliu.test7.repository;

import com.daomanliu.test7.entity.Role;
import com.daomanliu.test7.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByName(String name);

    @Query(value = "select r.* from roles r join user_role ur on r.id = ur.role_id where ur.user_id = ?1", nativeQuery = true)
    List<Role> findRolesByUserId(Long userId);
}
