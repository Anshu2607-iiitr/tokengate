package com.anshu.tokengate.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    boolean existsByEventId(String eventId);

    @Query("""
            select count(u) as requests,
                   sum(u.totalTokens) as totalTokens,
                   sum(u.costUsd) as totalCost
            from UsageRecord u
            where u.teamId = :teamId and u.createdAt >= :from
            """)
    UsageTotals totalsForTeamSince(@Param("teamId") Long teamId, @Param("from") Instant from);

    @Query("""
            select u.model as model,
                   count(u) as requests,
                   sum(u.totalTokens) as totalTokens,
                   sum(u.costUsd) as totalCost
            from UsageRecord u
            where u.teamId = :teamId and u.createdAt >= :from
            group by u.model
            order by sum(u.costUsd) desc
            """)
    List<ModelTotals> totalsByModelSince(@Param("teamId") Long teamId, @Param("from") Instant from);

    interface UsageTotals {
        Long getRequests();
        Long getTotalTokens();
        BigDecimal getTotalCost();
    }

    interface ModelTotals {
        String getModel();
        Long getRequests();
        Long getTotalTokens();
        BigDecimal getTotalCost();
    }
}