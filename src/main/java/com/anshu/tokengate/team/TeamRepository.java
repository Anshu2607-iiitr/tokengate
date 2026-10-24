package com.anshu.tokengate.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByApiKeyHash(String apiKeyHash);

    boolean existsByName(String name);
}