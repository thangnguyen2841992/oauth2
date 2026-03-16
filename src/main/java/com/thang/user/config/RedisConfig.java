package com.thang.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisConfig {

//    @Bean
//    public LettuceConnectionFactory redisConnectionFactory() {
//        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration("localhost", 6379);
//        // cfg.setPassword(RedisPassword.of("yourpassword")); // nếu cần
//        return new LettuceConnectionFactory(cfg);
//    }
}
