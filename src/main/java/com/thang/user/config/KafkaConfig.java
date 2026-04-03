package com.thang.user.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
    @Bean
    NewTopic checkSendActiveEmailTopic() {
        return new NewTopic("send-email-active-response",2,(short) 1);
    }

}