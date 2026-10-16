package com.savex.verification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface EmailChallengeRepository extends JpaRepository<EmailChallenge,UUID> {}
