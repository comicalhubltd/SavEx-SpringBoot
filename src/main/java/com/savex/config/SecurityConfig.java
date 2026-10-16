package com.savex.config;

import com.savex.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    private static final String[] PUBLIC_PATHS = {
            "/", "/index.html", "/app.css", "/app.js", "/session.js", "/report-table.js", "/dashboard-menu.js", "/admin.html", "/admin.js", "/sw.js", "/manifest.webmanifest", "/icons/**",
            "/api/v1/auth/**", "/api/v1/money/webhook/flutterwave",
            "/swagger-ui.html", "/swagger-ui/**",
            "/api-docs/**",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            @org.springframework.beans.factory.annotation.Value("${savex.security.login-limit:30}") int loginLimit,
            @org.springframework.beans.factory.annotation.Value("${savex.security.register-limit:15}") int registerLimit,
            @org.springframework.beans.factory.annotation.Value("${savex.security.auth-window-seconds:300}") long windowSeconds) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Please sign in to continue\"}");
                }).accessDeniedHandler((request,response,exception)->{
                    response.setStatus(403);response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"This account does not have permission for this action\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/groups/public/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/assistants/savings","/api/v1/assistants/transactions/**").hasAnyRole("VERIFIED","MEMBER","ADMIN")
                        .requestMatchers("/api/v1/verification/admin-setup").hasAnyRole("ONBOARDING","VERIFIED","MEMBER","ADMIN")
                        .requestMatchers("/api/v1/verification/account-assessment").hasAnyRole("ONBOARDING","VERIFIED","MEMBER","ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/v1/wallet","/api/v1/groups/my","/api/v1/money/capabilities","/api/v1/money/orders").hasAnyRole("VERIFIED","MEMBER","ADMIN")
                        .requestMatchers("/api/v1/verification/email/**", "/api/v1/verification/email").hasAnyRole("ONBOARDING","VERIFIED","MEMBER","ADMIN")
                        .requestMatchers("/api/v1/identity/**").hasAnyRole("ONBOARDING","VERIFIED","MEMBER","ADMIN")
                        .anyRequest().hasAnyRole("MEMBER","ADMIN")
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new com.savex.security.AuthRateLimitFilter(loginLimit,registerLimit,20_000,windowSeconds,java.time.Clock.systemUTC()),JwtAuthFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
