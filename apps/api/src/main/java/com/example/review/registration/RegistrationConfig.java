package com.example.review.registration;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RegistrationConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
