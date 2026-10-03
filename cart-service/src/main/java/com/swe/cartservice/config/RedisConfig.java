package com.swe.cartservice.config;

import com.swe.cartservice.model.Cart;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {

        RedisTemplate<String, Cart> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());

        JacksonJsonRedisSerializer<Cart> valueSerializer = new JacksonJsonRedisSerializer<>(objectMapper, Cart.class);

        template.setValueSerializer(valueSerializer);

        template.afterPropertiesSet();

        return template;
    }
}
