package com.thang.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@lombok.RequiredArgsConstructor
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {
    private final AuthSocketAuthorization authorization;
    private final com.nihongo.security.SocketSessionRegistry sessions;
    @org.springframework.beans.factory.annotation.Value("${FRONTEND_ORIGIN:http://localhost:5173}") private String[] origins;
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(authorization);
    }
    @Override public void configureClientOutboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration){registration.interceptors(sessions.outboundGuard());}
    @Override public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registry){registry.setMessageSizeLimit(8192).setSendBufferSizeLimit(65536).setSendTimeLimit(10000);registry.addDecoratorFactory(sessions::decorate);}

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/native").setAllowedOrigins(origins);
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();

    }
}
