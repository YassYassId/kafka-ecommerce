package com.swe.cartservice.config;

import com.swe.cartservice.model.Cart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("cartRedisTemplate should configure RedisTemplate with String and Jackson serializers")
    void cartRedisTemplate_ShouldConfigureTemplateProperly() {
        // Arrange
        RedisConfig config = new RedisConfig();

        // Act
        RedisTemplate<String, Cart> template = config.cartRedisTemplate(connectionFactory, objectMapper);

        // Assert
        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isEqualTo(connectionFactory);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(JacksonJsonRedisSerializer.class);
    }
}
