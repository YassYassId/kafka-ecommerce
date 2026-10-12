package com.swe.cartservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CheckoutRedisConfig {

    private DefaultRedisScript<Long> loadScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> claimCheckoutScript() {
        return loadScript("redis/claim-checkout.lua");
    }

    @Bean
    public DefaultRedisScript<Long> completeCheckoutScript() {
        return loadScript("redis/complete-checkout.lua");
    }

    @Bean
    public DefaultRedisScript<Long> releaseCheckoutClaimScript() {
        return loadScript("redis/release-checkout-claim.lua");
    }
}