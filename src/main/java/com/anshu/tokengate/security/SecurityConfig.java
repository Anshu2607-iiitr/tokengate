package com.anshu.tokengate.security;

import com.anshu.tokengate.team.TeamService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TeamService teamService,
                                                   @Value("${tokengate.admin-token}") String adminToken) throws Exception {
        http
                // API hai, browser forms nahi: CSRF, login page, basic auth ki zarurat nahi
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // Har request apni key khud laati hai, server session yaad nahi rakhta
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules: kaun kahan ja sakta hai
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/v1/**").hasRole("TEAM")
                        .anyRequest().permitAll() // ping, swagger, actuator, mock provider
                )

                // 401 = pehchaan nahi, 403 = pehchaan hai par permission nahi
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJson(response, 401, "Unauthorized: missing or invalid credentials"))
                        .accessDeniedHandler((request, response, e) ->
                                writeJson(response, 403, "Forbidden: your credentials do not allow this action"))
                )

                .addFilterBefore(new ApiKeyAuthFilter(teamService, adminToken),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}