package com.example.review.config;

import com.example.review.auth.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper,
            ApiErrorResponseFactory errorResponseFactory
    )
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> writeError(
                                response,
                                objectMapper,
                                errorResponseFactory.body(HttpStatus.UNAUTHORIZED, "Authentication is required", request)
                        ))
                        .accessDeniedHandler((request, response, ex) -> writeError(
                                response,
                                objectMapper,
                                errorResponseFactory.body(HttpStatus.FORBIDDEN, "Access is denied", request)
                        ))
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/verify-email", "/api/health", "/api/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/conferences/cfp", "/api/conferences/cfp/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/decisions/manuscripts/*/package").hasRole("AUTHOR")
                        .requestMatchers("/api/decisions/**").hasAnyRole("CHAIR", "ADMIN")
                        .requestMatchers("/api/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(Customizer.withDefaults());

        return http.build();
    }

    private void writeError(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            Map<String, Object> body
    ) throws IOException {
        int status = (Integer) body.get("status");
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Object traceId = body.get("traceId");
        if (traceId instanceof String value && !value.isBlank()) {
            response.setHeader(ApiErrorResponseFactory.TRACE_ID_HEADER, value);
        }
        objectMapper.writeValue(response.getWriter(), body);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
