package com.example.lms.service;

import com.example.lms.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetTokenCleanupJob {

    private final PasswordResetTokenRepository tokenRepository;

    @Scheduled(cron = "${app.cleanup.password-reset-token.cron:0 0 3 * * *}") // default: 03:00 daily
    @Transactional
    public void purgeExpiredOrUsedTokens() {
        int deleted = tokenRepository.deleteExpiredOrUsedTokens(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Password reset token cleanup: removed {} expired/used token(s).", deleted);
        }
    }
}
