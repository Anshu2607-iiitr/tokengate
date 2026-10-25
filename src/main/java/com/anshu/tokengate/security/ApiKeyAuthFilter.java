package com.anshu.tokengate.security;

import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.team.TeamService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Authentication: "ye request kiski hai?" Sirf pehchaanta hai, block nahi karta.
// Block karna (authorization) SecurityConfig ke rules ka kaam hai.
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final TeamService teamService;
    private final byte[] adminToken;

    public ApiKeyAuthFilter(TeamService teamService, String adminToken) {
        this.teamService = teamService;
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object principal = null;

        // 1. Admin token (constant-time compare: timing attack se bachav)
        String admin = request.getHeader("X-Admin-Token");
        if (admin != null && MessageDigest.isEqual(admin.getBytes(StandardCharsets.UTF_8), adminToken)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            principal = "admin";
        }

        // 2. Team API key
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<Team> team = teamService.findActiveByApiKey(header.substring(7).trim());
            if (team.isPresent()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_TEAM"));
                principal = team.get();
            }
        }

        if (principal != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
            SecurityContextHolder.setContext(context);
        }

        chain.doFilter(request, response);
    }
}