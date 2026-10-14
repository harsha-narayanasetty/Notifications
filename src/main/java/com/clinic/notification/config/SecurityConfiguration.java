package com.clinic.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

    @Configuration
    public class SecurityConfiguration {

        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a -> a.requestMatchers("/notifications").permitAll()
                            .anyRequest().denyAll())
                    .csrf(AbstractHttpConfigurer::disable)
                    .build();
        }

    }
