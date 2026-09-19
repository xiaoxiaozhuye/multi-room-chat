package com.multichat.infrastructure.mapper;

import com.multichat.auth.entity.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface UserMapper {
    @Select("""
            SELECT id, username, email, password_hash, role::text AS role, status::text AS status, created_at, updated_at, display_name, avatar_url, level, bio
            FROM users WHERE lower(username) = lower(#{username}) AND deleted_at IS NULL
            """)
    Optional<UserAccount> findByUsername(String username);

    @Select("""
            SELECT id, username, email, password_hash, role::text AS role, status::text AS status, created_at, updated_at, display_name, avatar_url, level, bio
            FROM users WHERE id = #{id} AND status = 'ACTIVE' AND deleted_at IS NULL
            """)
    Optional<UserAccount> findActiveById(java.util.UUID id);

    /** Serializes a sender's submit/retry race inside the caller's transaction. */
    @Select("""
            SELECT id, username, email, password_hash, role::text AS role, status::text AS status, created_at, updated_at, display_name, avatar_url, level, bio
            FROM users WHERE id = #{id} AND status = 'ACTIVE' AND deleted_at IS NULL
            FOR UPDATE
            """)
    Optional<UserAccount> findActiveByIdForUpdate(java.util.UUID id);

    @Insert("""
            INSERT INTO users (id, username, email, password_hash, role, status, display_name, avatar_url, level, bio)
            VALUES (#{id}, #{username}, #{email}, #{passwordHash}, CAST(#{role} AS user_role), CAST(#{status} AS user_status), #{displayName}, #{avatarUrl}, #{level}, #{bio})
            """)
    int insert(UserAccount user);

    @org.apache.ibatis.annotations.Update("""
            UPDATE users SET display_name = #{displayName}, avatar_url = #{avatarUrl}, bio = #{bio}, updated_at = clock_timestamp(), version = version + 1
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateProfile(UserAccount user);
}
