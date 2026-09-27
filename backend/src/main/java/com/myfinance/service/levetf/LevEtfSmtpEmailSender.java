package com.myfinance.service.levetf;

import com.myfinance.model.AppUser;
import com.myfinance.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP implementation of {@link LevEtfEmailSender}. Registered as a bean ONLY when
 * {@code app.mail.enabled=true}, so the default deployment (no SMTP) never wires it and email is a
 * no-op. Requires standard {@code spring.mail.*} settings from config/env; the sender never logs
 * credentials and swallows nothing — failures propagate to the alert service which records FAILED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class LevEtfSmtpEmailSender implements LevEtfEmailSender {

    private final JavaMailSender mailSender;
    private final AppUserRepository userRepository;

    @Value("${app.mail.from:no-reply@myfinance.local}")
    private String from;

    @Override
    public void send(Long userId, String subject, String body) {
        String to = userRepository.findById(userId).map(AppUser::getEmail).orElse(null);
        if (to == null || to.isBlank()) {
            log.debug("No email on file for userId={}; skipping alert email", userId);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }
}
