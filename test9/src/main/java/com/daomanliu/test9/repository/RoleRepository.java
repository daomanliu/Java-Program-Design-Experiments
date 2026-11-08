package com.daomanliu.test9.repository;

import com.daomanliu.test9.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Role findByName(String name);

    @Modifying
    @Query(value = "insert into user_role (user_id, role_id) values (?1, ?2)", nativeQuery = true)
    int insertUserRole(Long userId, Long roleId);


    @Query(value = "select r.* from roles r join user_role ur on r.id = ur.role_id where ur.user_id = ?1", nativeQuery = true)
    List<Role> findRolesByUserId(Long userId);
}
