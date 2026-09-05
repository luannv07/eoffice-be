package vn.edu.epu.eoffice_be.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineConfig {

    @Bean("rateLimitCacheBuilder")
    public Caffeine<Object, Object> rateLimitCacheBuilder(RateLimitProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(properties.getEndpointWindowSeconds()))
                .maximumSize(properties.getMaximumSize());
    }
}
