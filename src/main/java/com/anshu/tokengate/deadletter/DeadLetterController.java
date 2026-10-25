package com.anshu.tokengate.deadletter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/dead-letters")
public class DeadLetterController {

    private final DeadLetterRepository repository;

    public DeadLetterController(DeadLetterRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<DeadLetter> latest() {
        return repository.findTop50ByOrderByReceivedAtDesc();
    }
}