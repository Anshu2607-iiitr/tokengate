package com.anshu.tokengate.security;

import com.anshu.tokengate.team.Team;
import com.anshu.tokengate.team.TeamService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String TEAM_ATTRIBUTE = "authenticatedTeam";

    private final TeamService teamService;
    private final String adminToken;

    public ApiKeyAuthFilter(TeamService teamService,
                            @Value("${tokengate.admin-token}") String adminToken) {
        this.teamService = teamService;
        this.adminToken = adminToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/admin")) {
            String token = request.getHeader("X-Admin-Token");
            if (token == null || !token.equals(adminToken)) {
                reject(response, "Invalid or missing X-Admin-Token");
                return;
            }
        } else if (path.startsWith("/v1/")) {
            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                reject(response, "Missing API key. Use header: Authorization: Bearer <key>");
                return;
            }
            String apiKey = header.substring(7).trim();
            Optional<Team> team = teamService.findActiveByApiKey(apiKey);
            if (team.isEmpty()) {
                reject(response, "Invalid API key");
                return;
            }
            request.setAttribute(TEAM_ATTRIBUTE, team.get());
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}