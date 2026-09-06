package com.multichat.infrastructure.mapper;

import com.multichat.auth.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserMapper {
    @Select("""
            SELECT id, username, email, password_hash, role::text AS role, status::text AS status, created_at, updated_at
            FROM users WHERE username = #{username} AND status = 'ACTIVE' AND deleted_at IS NULL
            """)
    Optional<UserAccount> findActiveByUsername(String username);
}
