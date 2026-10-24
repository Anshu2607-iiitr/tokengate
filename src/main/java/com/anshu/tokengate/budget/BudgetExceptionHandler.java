package com.anshu.tokengate.budget;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class BudgetExceptionHandler {

    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<Map<String, Object>> handleBudgetExceeded(BudgetExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "error", "Daily budget exceeded",
                "team", ex.getTeamName(),
                "spentUsd", ex.getSpentUsd(),
                "budgetUsd", ex.getBudgetUsd()
        ));
    }
}