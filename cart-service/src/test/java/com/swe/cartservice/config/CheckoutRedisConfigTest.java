package com.swe.cartservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRedisConfigTest {

    private final CheckoutRedisConfig config = new CheckoutRedisConfig();

    @Test
    @DisplayName("claimCheckoutScript should configure RedisScript with Long result type and load script content")
    void claimCheckoutScript_ShouldConfigureScriptProperly() {
        DefaultRedisScript<Long> script = config.claimCheckoutScript();

        assertThat(script).isNotNull();
        assertThat(script.getResultType()).isEqualTo(Long.class);
        assertThat(script.getScriptAsString()).contains("KEYS[1]");
        assertThat(script.getScriptAsString()).contains("KEYS[2]");
    }

    @Test
    @DisplayName("completeCheckoutScript should configure RedisScript with Long result type and load script content")
    void completeCheckoutScript_ShouldConfigureScriptProperly() {
        DefaultRedisScript<Long> script = config.completeCheckoutScript();

        assertThat(script).isNotNull();
        assertThat(script.getResultType()).isEqualTo(Long.class);
        assertThat(script.getScriptAsString()).contains("KEYS[3]");
    }

    @Test
    @DisplayName("releaseCheckoutClaimScript should configure RedisScript with Long result type and load script content")
    void releaseCheckoutClaimScript_ShouldConfigureScriptProperly() {
        DefaultRedisScript<Long> script = config.releaseCheckoutClaimScript();

        assertThat(script).isNotNull();
        assertThat(script.getResultType()).isEqualTo(Long.class);
        assertThat(script.getScriptAsString()).contains("KEYS[1]");
    }
}
