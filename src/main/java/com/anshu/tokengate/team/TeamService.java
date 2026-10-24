package com.anshu.tokengate.team;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class TeamService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Transactional
    public CreatedTeam createTeam(String name) {
        if (teamRepository.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Team already exists: " + name);
        }
        String apiKey = generateApiKey();
        Team team = new Team(name, hash(apiKey), apiKey.substring(0, 10));
        teamRepository.save(team);
        return new CreatedTeam(team, apiKey);
    }

    @Transactional
    public Team setDailyBudget(Long teamId, BigDecimal dailyBudgetUsd) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found: " + teamId));
        team.setDailyBudgetUsd(dailyBudgetUsd);
        return teamRepository.save(team);
    }

    public Optional<Team> findActiveByApiKey(String apiKey) {
        return teamRepository.findByApiKeyHash(hash(apiKey))
                .filter(Team::isActive);
    }

    public List<Team> listTeams() {
        return teamRepository.findAll();
    }

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "tg_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record CreatedTeam(Team team, String apiKey) {}
}