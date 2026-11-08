CREATE DATABASE test6 DEFAULT CHARSET utf8mb4;
USE test6;

CREATE TABLE users (
                       id       BIGINT PRIMARY KEY AUTO_INCREMENT,
                       name     VARCHAR(50)  NOT NULL UNIQUE,
                       password VARCHAR(100) NOT NULL
);

CREATE TABLE roles (
                       id   BIGINT PRIMARY KEY AUTO_INCREMENT,
                       name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_role (
                           user_id BIGINT NOT NULL,
                           role_id BIGINT NOT NULL,
                           PRIMARY KEY (user_id, role_id)
);

-- roles 表建出来就行，不用插数据（你自己说的嘛）
-- 等有用户注册后，手动给他授权：
-- INSERT INTO roles (name) VALUES ('ADMIN');
-- INSERT INTO user_role (user_id, role_id) VALUES (1, 1);