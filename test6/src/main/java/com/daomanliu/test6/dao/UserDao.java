package com.daomanliu.test6.dao;

import com.daomanliu.test6.entity.Role;
import com.daomanliu.test6.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserDao {
    private final JdbcTemplate jdbc;

    public UserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public User findByName(String name) {
        List<User> list = jdbc.query("select id, name, password from users where name = ? ",
                (rs, i) -> new User(rs.getLong("id"),  rs.getString("name"), rs.getString("password")), name);
        return list.isEmpty() ? null : list.get(0);
    }

    public int insert(String name, String encodedPassword) {
        return jdbc.update("insert into users (name, password) values (?, ?)", name, encodedPassword);
    }

    public List<Role> findRolesByUserId(Long userId) {
        return jdbc.query("SELECT r.id, r.name FROM roles r JOIN user_role ur ON r.id = ur.role_id WHERE ur.user_id = ?",
                (rs, i) -> new Role(rs.getLong("id"), rs.getString("name")),
                userId
                );
    }

    public List<User> findAll() {
        return jdbc.query("select id, name, password from users",
                (rs, i) -> new User(rs.getLong("id"), rs.getString("name"), rs.getString("password")));
    }

}
