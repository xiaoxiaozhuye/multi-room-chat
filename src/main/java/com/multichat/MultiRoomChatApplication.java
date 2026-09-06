package com.multichat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@MapperScan("com.multichat.infrastructure.mapper")
@EnableScheduling
public class MultiRoomChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(MultiRoomChatApplication.class, args);
    }
}
