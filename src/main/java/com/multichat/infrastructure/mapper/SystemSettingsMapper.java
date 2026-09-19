package com.multichat.infrastructure.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface SystemSettingsMapper {
    @Select("SELECT setting_value FROM system_settings WHERE setting_key = #{key}")
    Optional<String> find(@Param("key") String key);

    @Insert("""
            INSERT INTO system_settings (setting_key, setting_value, updated_by, updated_at)
            VALUES (#{key}, #{value}, #{actorId}, #{updatedAt})
            ON CONFLICT (setting_key) DO UPDATE SET
                setting_value = EXCLUDED.setting_value,
                updated_by = EXCLUDED.updated_by,
                updated_at = EXCLUDED.updated_at
            """)
    int upsert(@Param("key") String key, @Param("value") String value,
               @Param("actorId") UUID actorId, @Param("updatedAt") Instant updatedAt);
}
